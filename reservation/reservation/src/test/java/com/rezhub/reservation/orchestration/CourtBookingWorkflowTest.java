package com.rezhub.reservation.orchestration;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.infrastructure.StripeService;
import com.rezhub.reservation.payment.PaymentGate;
import com.rezhub.reservation.payment.PlayerPaymentProfileEntity;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.view.ResourcesByFacilityView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CourtBookingWorkflowTest extends TestKitSupport {

    private CourtBookingWorkflow workflow() {
        var stripeService = new StripeService();
        var paymentGate = new PaymentGate(componentClient, stripeService);
        var courtDirectory = new CourtDirectoryAkka(componentClient);
        var reservationGateway = new ReservationGatewayAkka(componentClient);
        return new CourtBookingWorkflow(courtDirectory, reservationGateway, componentClient, paymentGate, stripeService);
    }

    private String createFacilityAndResource() throws Exception {
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));

        eventually(() -> componentClient.forView()
                .method(ResourcesByFacilityView::getByFacilityId)
                .invoke(facilityId),
            rows -> !rows.entries().isEmpty());

        return facilityId;
    }

    @Test
    public void book_playerWithPaymentMethod_proceedsUnchanged() throws Exception {
        String userId = "user-" + shortId();
        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::linkCustomer).invoke("cus_1");
        componentClient.forKeyValueEntity(userId).method(PlayerPaymentProfileEntity::setDefaultPaymentMethod).invoke("pm_1");
        String facilityId = createFacilityAndResource();

        var origin = new OriginRequestContext("telegram", "tg-1", "Alice", "recipient-1", "conv-1",
            Map.of(), Optional.of(userId));
        var context = new BookingContext("courts", facilityId, "Europe/Rome", Map.of());
        var intent = new BookingIntent(BookingIntent.BookingAction.BOOK, LocalDateTime.now().plusDays(1),
            60, List.of("Alice"), List.of(), null, Map.of());

        BookingHandle result = workflow().book(origin, context, intent);

        assertThat(result).isInstanceOf(BookingHandle.Booked.class);
        String reservationId = ((BookingHandle.Booked) result).handle().reservationId();
        var state = eventually(() ->
                componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::getReservation).invoke(),
            s -> s.state() != com.rezhub.reservation.reservation.ReservationState.State.INIT);
        assertThat(state.reservationId()).isEqualTo(reservationId);
    }

    private <T> T eventually(CheckedSupplier<T> query, java.util.function.Predicate<T> until) throws Exception {
        T last = null;
        for (int i = 0; i < 100; i++) {
            last = query.get();
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met after 5s. Last value: " + last);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
