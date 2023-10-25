package com.rezhub.reservation.actions;

import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.rezhub.reservation.dto.Reservation.FACILITY;
import static com.rezhub.reservation.dto.Reservation.RESOURCE;

public class ReservationActionTest {
    private static final Logger log = LoggerFactory.getLogger(ReservationActionTest.class);
    @Test
    public void testExtractPrefix() {
        String id = "3215t";
        String type = ReservationAction.extractPrefix(id);

        var action = switch (type) {
            case FACILITY -> "facility";
            case RESOURCE -> "resource";
            default -> "resource";
        };
        System.out.println("action = " + action);
    }
}
