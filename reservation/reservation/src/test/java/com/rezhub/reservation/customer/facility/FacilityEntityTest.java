package com.rezhub.reservation.customer.facility;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FacilityEntityTest {

    private static final Address ADDRESS = new Address("street", "city");

    @Test
    public void testCreateFacility() {
        var facility = new Facility("TCL", ADDRESS, Collections.emptySet(), "Europe/Berlin", null, null);
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);

        var result = testKit.method(FacilityEntity::create).invoke(facility);

        var event = result.getNextEventOfType(FacilityEvent.Created.class);
        assertEquals("TCL", event.facility().name());
    }

    @Test
    public void testCreateFacility_storesTimezoneAndBotToken() {
        var facility = new Facility("Tennis Club", ADDRESS, Collections.emptySet(),
            "America/New_York", "bot:12345", Set.of("user1", "user2"));
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);

        testKit.method(FacilityEntity::create).invoke(facility);

        var state = testKit.getState();
        assertThat(state.timezone()).isEqualTo("America/New_York");
        assertThat(state.botToken()).isEqualTo("bot:12345");
        assertThat(state.adminUserIds()).containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    public void testCreateFacility_nullTimezoneIsAllowed() {
        var facility = new Facility("Tennis Club", ADDRESS, Collections.emptySet(), null, null, null);
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);

        var result = testKit.method(FacilityEntity::create).invoke(facility);

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().timezone()).isNull();
        assertThat(testKit.getState().botToken()).isNull();
    }

    @Test
    public void testRequestResourceCreateAndRegister_carriesCalendarId() {
        var facility = new Facility("TCL", ADDRESS, Collections.emptySet(), "Europe/Berlin", null, null);
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);
        testKit.method(FacilityEntity::create).invoke(facility);

        var command = new FacilityEntity.CreateAndRegisterResource(
            "Court 1", "court-1", "cal123@group.calendar.google.com");

        var result = testKit.method(FacilityEntity::requestResourceCreateAndRegister).invoke(command);

        var event = result.getNextEventOfType(FacilityEvent.ResourceCreateAndRegisterRequested.class);
        assertThat(event.calendarId()).isEqualTo("cal123@group.calendar.google.com");
        assertThat(event.resourceName()).isEqualTo("Court 1");
        assertThat(event.resourceId()).isEqualTo("court-1");
    }

    @Test
    public void testAddResourceId() {
        var facilityId = "stub-facility-id";
        var testKit = EventSourcedTestKit.of(facilityId, FacilityEntity::new);

        var rId1 = "abc123";
        var result1 = testKit.method(FacilityEntity::registerResource).invoke(rId1);
        assertEquals(rId1, result1.getReply());
        assertEquals(rId1, result1.getNextEventOfType(FacilityEvent.ResourceRegistered.class).resourceId());

        var rId2 = "abc234";
        var result2 = testKit.method(FacilityEntity::registerResource).invoke(rId2);
        assertEquals(rId2, result2.getReply());

        assertEquals(2, testKit.getAllEvents().size());
        var state = testKit.getState();
        assertEquals(2, state.resourceIds().size());
        Assertions.assertEquals(
            new FacilityState(facilityId, state.name(), state.addressState(), state.resourceIds(),
                state.timezone(), state.botToken(), state.adminUserIds()),
            state);
    }

    @Test
    public void testDateTimeString() {
        var dateTime = "2023-07-18T11:00";
        assertEquals(dateTime, LocalDateTime.parse(dateTime).toString());
    }
}
