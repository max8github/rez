package com.rezhub.reservation.orchestration;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.infrastructure.StripeService;
import com.rezhub.reservation.payment.PaymentGate;
import com.rezhub.reservation.payment.PlayerPaymentProfileEntity;
import com.rezhub.reservation.payment.PlayerPaymentProfileState;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.resource.dto.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** BookingWorkflow implementation for the court-booking domain. */
public class CourtBookingWorkflow implements BookingWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CourtBookingWorkflow.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CourtDirectoryAkka courtDirectory;
    private final ReservationGatewayAkka reservationGateway;
    private final ComponentClient componentClient;
    private final PaymentGate paymentGate;
    private final StripeService stripeService;

    public CourtBookingWorkflow(CourtDirectoryAkka courtDirectory,
                                ReservationGatewayAkka reservationGateway,
                                ComponentClient componentClient,
                                PaymentGate paymentGate,
                                StripeService stripeService) {
        this.courtDirectory = courtDirectory;
        this.reservationGateway = reservationGateway;
        this.componentClient = componentClient;
        this.paymentGate = paymentGate;
        this.stripeService = stripeService;
    }

    @Override
    public String domain() {
        return "courts";
    }

    @Override
    public AvailabilityResult checkAvailability(OriginRequestContext origin, BookingContext context, BookingIntent intent) {
        String facilityId = context.scopeId();
        LocalDateTime requestedStart = intent.dateTime();
        int durationMinutes = effectiveDuration(intent);
        LocalDateTime requestedEnd = requestedStart.plusMinutes(durationMinutes);
        log.debug("checkAvailability: facilityId={}, time={}", facilityId, requestedStart);

        ResourceView.Resources resources = componentClient.forView()
            .method(ResourceView::getResource)
            .invoke(facilityId);

        if (resources.resources().isEmpty()) {
            return new AvailabilityResult(facilityId, List.of(),
                Map.of("message", "No courts registered for this facility."));
        }

        List<String> available = resources.resources().stream()
            .filter(r -> isAvailableAt(r, requestedStart, requestedEnd))
            .map(r -> r.resourceName() + " (id: " + r.resourceId() + ")")
            .toList();

        if (available.isEmpty()) {
            String alternatives = findNearbySlots(resources.resources(), requestedStart, durationMinutes);
            return new AvailabilityResult(facilityId, List.of(), Map.of("alternatives", alternatives));
        }

        return new AvailabilityResult(facilityId, available, Map.of());
    }

    @Override
    public BookingHandle book(OriginRequestContext origin, BookingContext context, BookingIntent intent) {
        CourtBookingScope scope = courtDirectory.resolveScope(context);
        log.info("book: facilityId={}, resources={}, dateTime={}", scope.facilityId(), scope.resourceIds().size(), intent.dateTime());

        // FR-012: facility-side gate — needs no player identity, applies uniformly (research.md #10).
        if (!paymentGate.isFacilityPayable(scope.facilityId())) {
            log.info("book: rejecting — facility {} has a PricingPolicy but incomplete Stripe onboarding", scope.facilityId());
            return new BookingHandle.FacilityNotPayable();
        }

        // FR-005: player-side gate — only meaningful when a resolved identity exists, and only when
        // the facility actually charges at all (a free facility has nothing to collect on, so there's
        // no reason to demand a card on file).
        if (paymentGate.facilityRequiresPayment(scope.facilityId()) && !paymentGate.isPlayerPayable(origin.identityUserId())) {
            String returnUrl = origin.attributes().getOrDefault("returnUrl", "https://t.me/");
            String checkoutUrl = createCardSetupLinkOrNull(origin.identityUserId(), returnUrl);
            log.info("book: rejecting — no payment method on file for identity {}", origin.identityUserId());
            return new BookingHandle.CardSetupRequired(checkoutUrl);
        }

        String reservationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Optional<String> senderExternalId = origin.senderExternalId() == null || origin.senderExternalId().isBlank()
            ? Optional.empty() : Optional.of(origin.senderExternalId());
        ReservationSubmission submission = new ReservationSubmission(
            reservationId,
            origin.recipientId(),
            scope.timezone(),
            intent.dateTime(),
            effectiveDuration(intent),
            intent.participantNames(),
            Set.copyOf(scope.resourceIds()),
            origin.origin(),
            origin.identityUserId(),
            senderExternalId
        );
        return new BookingHandle.Booked(reservationGateway.submit(submission));
    }

    /**
     * FR-005's card-collection link. {@code identityUserId} is only absent when the requester has no
     * resolved identity at all (e.g. a message with no identifiable Telegram sender) — in that case
     * there's no {@code PlayerPaymentProfile} to eventually populate, so no link is offered either.
     *
     * <p>A Checkout Session in setup mode does not create a Stripe Customer on its own — confirmed
     * against a live test event, where both the resulting SetupIntent's and the completed session's
     * {@code customer} field came back {@code null}. Without a customer, the collected payment method
     * is never attached to anything {@code PlayerPaymentProfile} can reference, so a card-setup link
     * needs an already-existing customer passed in explicitly. Reuse one from a prior (possibly
     * incomplete) attempt if {@code PlayerPaymentProfile} already has it; otherwise create one now and
     * persist it immediately, so a retry after a failed/abandoned checkout reuses the same customer.
     */
    private String createCardSetupLinkOrNull(Optional<String> identityUserId, String returnUrl) {
        if (identityUserId.isEmpty()) {
            return null;
        }
        String userId = identityUserId.get();
        try {
            PlayerPaymentProfileState profile = componentClient
                .forKeyValueEntity(userId)
                .method(PlayerPaymentProfileEntity::getProfile)
                .invoke();
            String stripeCustomerId = profile.stripeCustomerId().orElse(null);
            if (stripeCustomerId == null) {
                stripeCustomerId = stripeService.createCustomer(userId, null);
                componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::linkCustomer).invoke(stripeCustomerId);
            }
            return stripeService.createCardSetupLink(stripeCustomerId, userId, returnUrl);
        } catch (Exception e) {
            log.error("Failed to create card-setup link for identity {}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public void cancel(OriginRequestContext origin, BookingContext context, CancelIntent intent) {
        log.info("cancel: reservationId={}", intent.reservationId());
        reservationGateway.cancel(intent.reservationId());
    }

    private static int effectiveDuration(BookingIntent intent) {
        return intent.durationMinutes() != null ? intent.durationMinutes() : Reservation.DEFAULT_DURATION_MINUTES;
    }

    private boolean isAvailableAt(ResourceV resource, LocalDateTime requestedStart, LocalDateTime requestedEnd) {
        return resource.timeWindow().stream().noneMatch(entry -> overlaps(entry, requestedStart, requestedEnd));
    }

    private boolean overlaps(Resource.Entry entry, LocalDateTime start, LocalDateTime end) {
        LocalDateTime entryStart = parseOrNull(entry.startTime());
        LocalDateTime entryEnd = parseOrNull(entry.endTime());
        if (entryStart == null || entryEnd == null) return false;
        return entryStart.isBefore(end) && start.isBefore(entryEnd);
    }

    private static LocalDateTime parseOrNull(String s) {
        try {
            return LocalDateTime.parse(s, ISO_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private String findNearbySlots(List<ResourceV> resources, LocalDateTime around, int durationMinutes) {
        boolean anyBookingNearby = resources.stream()
            .flatMap(r -> r.timeWindow().stream())
            .map(Resource.Entry::startTime)
            .map(CourtBookingWorkflow::parseOrNull)
            .anyMatch(t -> t != null && !t.isBefore(around.minusHours(2)) && !t.isAfter(around.plusHours(4)));

        if (!anyBookingNearby) {
            return "The facility appears to have open slots — try a slightly different time.";
        }

        for (int offset = 1; offset <= 4; offset++) {
            LocalDateTime candidateStart = around.plusHours(offset).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime candidateEnd = candidateStart.plusMinutes(durationMinutes);
            long busyCourts = resources.stream()
                .filter(r -> r.timeWindow().stream().anyMatch(e -> overlaps(e, candidateStart, candidateEnd)))
                .count();
            if (busyCourts < resources.size()) {
                return "The next available slot is around "
                    + candidateStart.format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
            }
        }
        return "No alternative slots found in the next 4 hours.";
    }
}
