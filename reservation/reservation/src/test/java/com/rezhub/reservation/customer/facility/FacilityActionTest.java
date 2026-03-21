package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.dto.EntityType;
import com.rezhub.reservation.dto.SelectionItem;
import org.junit.jupiter.api.Test;

import static com.rezhub.reservation.dto.Reservation.FACILITY;
import static com.rezhub.reservation.dto.Reservation.RESOURCE;
import static org.junit.jupiter.api.Assertions.*;

class FacilityActionTest {

    @Test
    public void selectionItemCarriesExplicitType() {
        var facilityItem = new SelectionItem(FACILITY + "court1", EntityType.FACILITY);
        var resourceItem = new SelectionItem(RESOURCE + "court1", EntityType.RESOURCE);

        assertEquals(EntityType.FACILITY, facilityItem.type());
        assertEquals(EntityType.RESOURCE, resourceItem.type());
    }
}
