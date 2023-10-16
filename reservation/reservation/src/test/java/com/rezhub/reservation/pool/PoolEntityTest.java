package com.rezhub.reservation.pool;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.pool.dto.Pool;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationEvent;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PoolEntityTest {

    @Test
    public void testCreateFacility() {
        Pool pool = new Pool("TCL", Collections.emptySet());

        var testKit = EventSourcedTestKit.of(PoolEntity::new);
        var facilityResult = testKit.call(e -> e.create(pool));
        var facilityCreated = facilityResult.getNextEventOfType(PoolEvent.Created.class);
        assertEquals("TCL", facilityCreated.pool().name());
    }

    @Test
    public void testAddResourceId() {
        var testKit = EventSourcedTestKit.of(PoolEntity::new);
        var facilityId = "testkit-entity-id";

        {
            var rId = "abc123";
            var result = testKit.call(e -> e.addResourceId(rId));
            assertEquals(rId, result.getReply());

            var resourceAdded = result.getNextEventOfType(PoolEvent.ResourceIdAdded.class);
            assertEquals(rId, resourceAdded.resourceEntityId());
        }
        {
            var rId = "abc234";
            var result = testKit.call(e -> e.addResourceId(rId));
            assertEquals(rId, result.getReply());

            var resourceAdded = result.getNextEventOfType(PoolEvent.ResourceIdAdded.class);
            assertEquals(rId, resourceAdded.resourceEntityId());
        }

        assertEquals(testKit.getAllEvents().size(), 2);
        var result = testKit.getState();
        assertEquals(2, result.resourceIds().size());
        Assertions.assertEquals(
                new PoolState(facilityId, result.name(),
                        result.resourceIds()),
                result);

    }

    @Test
    public void testCreateReservation() {

        var testKit = EventSourcedTestKit.of(ReservationEntity::new);

        {
            var result = testKit.call(e -> e.init(new ReservationEntity.Init(
              new Reservation(List.of("john.doe@example.com"), LocalDateTime.of(2023, 8, 10, 0, 0)),
              Set.of("123"))));
            assertFalse(result.getReply().isEmpty());

            var event = result.getNextEventOfType(ReservationEvent.Inited.class);
            assertEquals(1, event.resources().size());
        }
    }

    @Test
    public void testDateTimeString() {
        var dateTime = "2023-07-18T11:00";
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime);
        assertEquals(dateTime, localDateTime.toString());
    }

}
