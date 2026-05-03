package com.rezhub.reservation.orchestration;

import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.resource.ResourceV;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.resource.dto.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** BookingWorkflow implementation for the court-booking domain. */
public class CourtBookingWorkflow implements BookingWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CourtBookingWorkflow.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CourtDirectoryAkka courtDirectory;
    private final ReservationGatewayAkka reservationGateway;
    private final ComponentClient componentClient;

    public CourtBookingWorkflow(CourtDirectoryAkka courtDirectory,
                                ReservationGatewayAkka reservationGateway,
                                ComponentClient componentClient) {
        this.courtDirectory = courtDirectory;
        this.reservationGateway = reservationGateway;
        this.componentClient = componentClient;
    }

    @Override
    public String domain() {
        return "courts";
    }

    @Override
    public AvailabilityResult checkAvailability(OriginRequestContext origin, BookingContext context, BookingIntent intent) {
        String facilityId = context.scopeId();
        LocalDateTime requestedTime = intent.dateTime();
        log.debug("checkAvailability: facilityId={}, time={}", facilityId, requestedTime);

        ResourceView.Resources resources = componentClient.forView()
            .method(ResourceView::getResource)
            .invoke(facilityId);

        if (resources.resources().isEmpty()) {
            return new AvailabilityResult(facilityId, List.of(),
                Map.of("message", "No courts registered for this facility."));
        }

        List<String> available = resources.resources().stream()
            .filter(r -> isAvailableAt(r, requestedTime))
            .map(r -> r.resourceName() + " (id: " + r.resourceId() + ")")
            .toList();

        if (available.isEmpty()) {
            String alternatives = findNearbySlots(resources.resources(), requestedTime);
            return new AvailabilityResult(facilityId, List.of(), Map.of("alternatives", alternatives));
        }

        return new AvailabilityResult(facilityId, available, Map.of());
    }

    @Override
    public ReservationHandle book(OriginRequestContext origin, BookingContext context, BookingIntent intent) {
        CourtBookingScope scope = courtDirectory.resolveScope(context);
        log.info("book: facilityId={}, resources={}, dateTime={}", scope.facilityId(), scope.resourceIds().size(), intent.dateTime());

        String reservationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ReservationSubmission submission = new ReservationSubmission(
            reservationId,
            origin.recipientId(),
            scope.timezone(),
            intent.dateTime(),
            intent.participantNames(),
            Set.copyOf(scope.resourceIds()),
            origin.origin()
        );
        return reservationGateway.submit(submission);
    }

    @Override
    public void cancel(OriginRequestContext origin, BookingContext context, CancelIntent intent) {
        log.info("cancel: reservationId={}", intent.reservationId());
        reservationGateway.cancel(intent.reservationId());
    }

    private boolean isAvailableAt(ResourceV resource, LocalDateTime requestedTime) {
        String isoKey = requestedTime.format(ISO_FMT);
        return resource.timeWindow().stream()
            .noneMatch(entry -> entry.dateTime().equals(isoKey));
    }

    private String findNearbySlots(List<ResourceV> resources, LocalDateTime around) {
        List<LocalDateTime> bookedTimes = resources.stream()
            .flatMap(r -> r.timeWindow().stream())
            .map(Resource.Entry::dateTime)
            .map(s -> { try { return LocalDateTime.parse(s, ISO_FMT); } catch (Exception e) { return null; } })
            .filter(t -> t != null && !t.isBefore(around.minusHours(2)) && !t.isAfter(around.plusHours(4)))
            .distinct()
            .sorted()
            .toList();

        if (bookedTimes.isEmpty()) {
            return "The facility appears to have open slots — try a slightly different time.";
        }

        for (int offset = 1; offset <= 4; offset++) {
            LocalDateTime candidate = around.plusHours(offset).withMinute(0).withSecond(0).withNano(0);
            String key = candidate.format(ISO_FMT);
            long busyCourts = resources.stream()
                .filter(r -> r.timeWindow().stream().anyMatch(e -> e.dateTime().equals(key)))
                .count();
            if (busyCourts < resources.size()) {
                return "The next available slot is around "
                    + candidate.format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
            }
        }
        return "No alternative slots found in the next 4 hours.";
    }
}
