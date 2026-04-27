package com.rezhub.reservation.customer.facility;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FacilityEntityTest {

    private static final Address ADDRESS = new Address("street", "city");

    @Test
    public void testCreateFacility() {
        var facility = new Facility("TCL", ADDRESS, "Europe/Berlin", null, null);
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);

        var result = testKit.method(FacilityEntity::create).invoke(facility);

        var event = result.getNextEventOfType(FacilityEvent.Created.class);
        assertEquals("TCL", event.facility().name());
    }

    @Test
    public void testCreateFacility_storesTimezoneAndBotToken() {
        var facility = new Facility("Tennis Club", ADDRESS, "America/New_York", "bot:12345", Set.of("user1", "user2"));
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);

        testKit.method(FacilityEntity::create).invoke(facility);

        var state = testKit.getState();
        assertThat(state.timezone()).isEqualTo("America/New_York");
        assertThat(state.botToken()).isEqualTo("bot:12345");
        assertThat(state.adminUserIds()).containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    public void testCreateFacility_nullTimezoneIsAllowed() {
        var facility = new Facility("Tennis Club", ADDRESS, null, null, null);
        var testKit = EventSourcedTestKit.of("stub-facility-id", FacilityEntity::new);

        var result = testKit.method(FacilityEntity::create).invoke(facility);

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().timezone()).isNull();
        assertThat(testKit.getState().botToken()).isNull();
    }

    @Test
    public void testDateTimeString() {
        var dateTime = "2023-07-18T11:00";
        assertEquals(dateTime, LocalDateTime.parse(dateTime).toString());
    }
}
