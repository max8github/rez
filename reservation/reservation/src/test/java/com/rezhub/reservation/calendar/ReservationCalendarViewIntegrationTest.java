package com.rezhub.reservation.calendar;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceView;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ReservationCalendarViewIntegrationTest extends TestKitSupport {

    @Test
    public void fulfilledReservation_appearsInCalendarView() throws Exception {
        String facilityId = "f_calendar-" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        LocalDateTime start = LocalDateTime.of(2026, 4, 5, 10, 0);
        Reservation reservation = new Reservation(java.util.List.of("Alice", "Bob"), start);

        createFacilityAndResource(facilityId, resourceId, "Center Court");
        waitForResource(resourceId);

        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(
                reservation,
                Set.of(resourceId),
                "telegram-user",
                "telegram"
            ));
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));

        ReservationCalendarView.ReservationEntries result = eventually(() ->
            componentClient.forView()
                .method(ReservationCalendarView::getEvents)
                .invoke(new ReservationCalendarView.ReservationRange(
                    start.minusDays(1).toString(),
                    start.plusDays(1).toString()
                )),
            entries -> !entries.reservations().isEmpty());

        assertThat(result.reservations()).singleElement().satisfies(entry -> {
            assertThat(entry.reservationId()).isEqualTo(reservationId);
            assertThat(entry.resourceId()).isEqualTo(resourceId);
            assertThat(entry.playerNames()).isEqualTo("Alice, Bob");
            assertThat(entry.startTime()).isEqualTo(start.toString());
            assertThat(entry.endTime()).isEqualTo(start.plusHours(1).toString());
            assertThat(entry.status()).isEqualTo("FULFILLED");
        });
    }

    @Test
    public void cancelledReservation_isHiddenFromCalendarQuery() throws Exception {
        String facilityId = "f_calendar-" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        LocalDateTime start = LocalDateTime.of(2026, 4, 6, 12, 0);
        Reservation reservation = new Reservation(java.util.List.of("Alice", "Bob"), start);

        createFacilityAndResource(facilityId, resourceId, "Court 2");
        waitForResource(resourceId);

        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::init)
            .invoke(new ReservationEntity.Init(
                reservation,
                Set.of(resourceId),
                "telegram-user",
                "telegram"
            ));
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::replyAvailability)
            .invoke(new ReservationEntity.ReplyAvailability(reservationId, resourceId, true));
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::fulfill)
            .invoke(new ReservationEntity.Fulfill(resourceId, reservationId, reservation));
        componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::cancel)
            .invoke();

        ReservationCalendarView.ReservationEntries result = eventually(() ->
            componentClient.forView()
                .method(ReservationCalendarView::getEvents)
                .invoke(new ReservationCalendarView.ReservationRange(
                    start.minusDays(1).toString(),
                    start.plusDays(1).toString()
                )),
            entries -> entries.reservations().isEmpty());

        assertThat(result.reservations()).isEmpty();
    }

    private void createFacilityAndResource(String facilityId, String resourceId, String resourceName) {
        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(new Facility("Calendar Club", new Address("Main Street", "Rome"), "Europe/Rome", null, null));

        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::create)
            .invoke(new Resource(resourceId, resourceName, "calendar-" + resourceId + "@example.test"));

        componentClient.forEventSourcedEntity(resourceId)
            .method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));
    }

    private void waitForResource(String resourceId) throws Exception {
        eventually(() ->
                componentClient.forView()
                    .method(ResourceView::getResourceById)
                    .invoke(resourceId),
            Optional::isPresent);
    }

    private <T> T eventually(CheckedSupplier<T> query, java.util.function.Predicate<T> until) throws Exception {
        T last = null;
        for (int i = 0; i < 80; i++) {
            last = query.get();
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met after 4s. Last value: " + last);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
