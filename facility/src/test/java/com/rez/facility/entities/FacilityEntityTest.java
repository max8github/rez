package com.rez.facility.entities;

import com.rez.facility.events.FacilityEvent;
import com.rez.facility.domain.Facility;
import com.rez.facility.dto.Address;
import com.rez.facility.dto.Reservation;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FacilityEntityTest {

    @Test
    public void testCreateFacility() {
        Address address = new Address("street", "city");
        com.rez.facility.dto.Facility facility = new com.rez.facility.dto.Facility("TCL", address, Collections.emptySet());

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
                new Facility(facilityId, result.name(),
                        result.address(),
                        result.resourceIds()),
                result);

    }

    @Test
    public void testCreateReservation() {

        var testKit = EventSourcedTestKit.of(FacilityEntity::new);

        {
            var result = testKit.call(e -> e.createReservation(new Reservation(
                    List.of("john.doe@example.com"), 0, LocalDate.of(2023, 8, 10))));
            assertTrue(result.getReply().length() > 0);

            var reservationCreated = result.getNextEventOfType(FacilityEvent.ReservationCreated.class);
            assertEquals(0, reservationCreated.resources().size());
        }


    }

}
