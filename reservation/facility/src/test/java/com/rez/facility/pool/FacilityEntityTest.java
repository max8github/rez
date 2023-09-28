package com.rez.facility.pool;

import com.rez.facility.dto.Reservation;
import com.rez.facility.pool.dto.Address;
import com.rez.facility.pool.dto.Facility;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            var result = testKit.call(e -> e.addResourceId(rId));
            assertEquals(rId, result.getReply());

            var resourceAdded = result.getNextEventOfType(FacilityEvent.ResourceIdAdded.class);
            assertEquals(rId, resourceAdded.resourceEntityId());
        }
        {
            var rId = "abc234";
            var result = testKit.call(e -> e.addResourceId(rId));
            assertEquals(rId, result.getReply());

            var resourceAdded = result.getNextEventOfType(FacilityEvent.ResourceIdAdded.class);
            assertEquals(rId, resourceAdded.resourceEntityId());
        }

        assertEquals(testKit.getAllEvents().size(), 2);
        var result = testKit.getState();
        assertEquals(2, result.resourceIds().size());
        Assertions.assertEquals(
                new com.rez.facility.pool.Facility(facilityId, result.name(),
                        result.address(),
                        result.resourceIds()),
                result);

    }

    @Test
    public void testCreateReservation() {

        var testKit = EventSourcedTestKit.of(FacilityEntity::new);

        {
            var result = testKit.call(e -> e.createReservation(new Reservation(
                    List.of("john.doe@example.com"), LocalDateTime.of(2023, 8, 10, 0, 0))));
            assertTrue(result.getReply().length() > 0);

            var reservationCreated = result.getNextEventOfType(FacilityEvent.ReservationCreated.class);
            assertEquals(0, reservationCreated.resources().size());
        }
    }

    @Test
    public void testDateTimeString() {
        var dateTime = "2023-07-18T11:00";
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime);
        assertEquals(dateTime, localDateTime.toString());
    }

}
