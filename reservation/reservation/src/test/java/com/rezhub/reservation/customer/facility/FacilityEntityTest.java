package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.customer.facility.dto.Address;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FacilityEntityTest {

    @Test
    public void testCreateFacility() {
        Address address = new Address("street", "city");
        Facility facility = new Facility("TCL", address, Collections.emptySet());

        var testKit = EventSourcedTestKit.of(FacilityEntity::new);
        var facilityResult = testKit.call(e -> e.create(facility));
        var facilityCreated = facilityResult.getNextEventOfType(FacilityEvent.Created.class);
        assertEquals("TCL", facilityCreated.facility().name());
    }

    @Test
    public void testAddResourceId() {
        var testKit = EventSourcedTestKit.of(FacilityEntity::new);
        var facilityId = "testkit-entity-id";

        {
            var rId = "abc123";
            var result = testKit.call(e -> e.registerResource(rId));
            assertEquals(rId, result.getReply());

            var resourceAdded = result.getNextEventOfType(FacilityEvent.ResourceRegistered.class);
            assertEquals(rId, resourceAdded.resourceId());
        }
        {
            var rId = "abc234";
            var result = testKit.call(e -> e.registerResource(rId));
            assertEquals(rId, result.getReply());

            var resourceAdded = result.getNextEventOfType(FacilityEvent.ResourceRegistered.class);
            assertEquals(rId, resourceAdded.resourceId());
        }

        assertEquals(testKit.getAllEvents().size(), 2);
        var result = testKit.getState();
        assertEquals(2, result.resourceIds().size());
        Assertions.assertEquals(
          new FacilityState(facilityId, result.name(),
            result.addressState(),
            result.resourceIds()),
          result);

    }

    @Test
    public void testDateTimeString() {
        var dateTime = "2023-07-18T11:00";
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime);
        assertEquals(dateTime, localDateTime.toString());
    }

}
