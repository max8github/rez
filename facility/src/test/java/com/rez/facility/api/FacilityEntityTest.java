package com.rez.facility.api;

import com.rez.facility.domain.Facility;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FacilityEntityTest {

    @Test
    public void testCreateFacility() {
        Mod.Address address = new Mod.Address("street", "city");
        Mod.Facility facility = new Mod.Facility("TCL", address, Collections.emptySet());

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
            var result = testKit.call(e -> e.createReservation(new Mod.Reservation("max", 0)));
            assertTrue(result.getReply().length() > 0);

            var reservationCreated = result.getNextEventOfType(FacilityEvent.ReservationCreated.class);
            assertEquals(0, reservationCreated.resources().size());
        }


    }

}
