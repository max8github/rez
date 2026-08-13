package com.rezhub.reservation.resource;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceEntityTest {

    private static final String CALENDAR_ID = "abc123@group.calendar.google.com";
    private static final String FACILITY_ID = "f_club1";
    private static final String RESOURCE_ID = "r_court1";

    @Test
    void createFacilityResource_storesCalendarIdOnState() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);
        var command = new ResourceEntity.CreateChildResource(
            FACILITY_ID,
            new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        var result = testKit.method(ResourceEntity::createFacilityResource).invoke(command);

        var event = result.getNextEventOfType(ResourceEvent.FacilityResourceCreated.class);
        assertThat(event.calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(event.parentId()).isEqualTo(FACILITY_ID);
        assertThat(event.name()).isEqualTo("Court 1");

        assertThat(testKit.getState().calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(testKit.getState().name()).isEqualTo("Court 1");
    }

    @Test
    void createResource_storesCalendarIdOnState() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        var event = result.getNextEventOfType(ResourceEvent.ResourceCreated.class);
        assertThat(event.calendarId()).isEqualTo(CALENDAR_ID);

        assertThat(testKit.getState().calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(testKit.getState().name()).isEqualTo("Court 1");
    }

    @Test
    void createResource_withNullCalendarId_isValid() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "Court 1", null));

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().calendarId()).isNull();
    }

    @Test
    void createResource_rejectsEmptyName() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "", CALENDAR_ID));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void createResource_rejectsForbiddenName() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, Resource.FORBIDDEN_NAME, CALENDAR_ID));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void deleteResource_persistsDeletionEvent() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);
        testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        var result = testKit.method(ResourceEntity::deleteResource).invoke();

        assertThat(result.getNextEventOfType(ResourceEvent.ResourceDeleted.class).resourceId()).isEqualTo(RESOURCE_ID);
    }

    /**
     * End-to-end regression for the fix: a resource that accepts a reservation must reject a second
     * one that genuinely overlaps in real time, even when the two requests round to different hours
     * under the old top-of-the-hour keying scheme.
     */
    @Test
    void reserve_rejectsGenuinelyOverlappingBooking_evenAcrossDifferentHours() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);
        testKit.method(ResourceEntity::create).invoke(new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));
        testKit.method(ResourceEntity::setBookingGranularity).invoke(30);

        LocalDateTime start = nextMonday().withHour(15).withMinute(30);
        var first = new Reservation(List.of("amy@example.com"), start, 60); // 15:30-16:30
        var firstResult = testKit.method(ResourceEntity::reserve)
            .invoke(new ResourceEntity.Reserve("rez-1", first));
        assertThat(firstResult.getReply()).isEqualTo("OK");

        var second = new Reservation(List.of("bob@example.com"), start.plusMinutes(30), 60); // 16:00-17:00
        var secondResult = testKit.method(ResourceEntity::reserve)
            .invoke(new ResourceEntity.Reserve("rez-2", second));

        assertThat(secondResult.getReply()).isEqualTo("UNAVAILABLE resource");
        assertThat(secondResult.getNextEventOfType(ResourceEvent.ReservationRejected.class)).isNotNull();
    }

    @Test
    void reserve_acceptsNonOverlappingBooking_inDifferentHour() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);
        testKit.method(ResourceEntity::create).invoke(new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        LocalDateTime start = nextMonday().withHour(15).withMinute(0);
        var first = new Reservation(List.of("amy@example.com"), start, 60); // 15:00-16:00
        testKit.method(ResourceEntity::reserve).invoke(new ResourceEntity.Reserve("rez-1", first));

        var second = new Reservation(List.of("bob@example.com"), start.plusMinutes(60), 60); // 16:00-17:00
        var secondResult = testKit.method(ResourceEntity::reserve)
            .invoke(new ResourceEntity.Reserve("rez-2", second));

        assertThat(secondResult.getReply()).isEqualTo("OK");
        assertThat(secondResult.getNextEventOfType(ResourceEvent.ReservationAccepted.class)).isNotNull();
    }

    @Test
    void cancel_releasesExactlyThatReservationsSlots() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);
        testKit.method(ResourceEntity::create).invoke(new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        LocalDateTime start = nextMonday().withHour(15).withMinute(0);
        var reservation = new Reservation(List.of("amy@example.com"), start, 60);
        testKit.method(ResourceEntity::reserve).invoke(new ResourceEntity.Reserve("rez-1", reservation));

        testKit.method(ResourceEntity::cancel).invoke(new ResourceEntity.CancelReservation("rez-1"));

        var checkResult = testKit.method(ResourceEntity::checkAvailability)
            .invoke(new ResourceEntity.CheckAvailability("rez-2", reservation));
        assertThat(checkResult.getNextEventOfType(ResourceEvent.AvalabilityChecked.class).available()).isTrue();
    }

    private static LocalDateTime nextMonday() {
        LocalDateTime d = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        while (d.getDayOfWeek() != java.time.DayOfWeek.MONDAY) d = d.plusDays(1);
        return d;
    }
}
