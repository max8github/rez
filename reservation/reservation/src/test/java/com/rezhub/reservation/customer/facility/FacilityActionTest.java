package com.rezhub.reservation.customer.facility;

import org.junit.jupiter.api.Test;

import static com.rezhub.reservation.dto.Reservation.FACILITY;
import static com.rezhub.reservation.dto.Reservation.RESOURCE;
import static org.junit.jupiter.api.Assertions.*;

class FacilityActionTest {
  @Test
  public void testExtractPrefix() {

    var resource = switch (FacilityAction.extractPrefix("3215t")) {
      case FACILITY -> "facility";
      case RESOURCE -> "resource";
      default -> "resource";
    };
    assertEquals("resource", resource);

    var facility = switch (FacilityAction.extractPrefix(FACILITY+"3215t")) {
      case FACILITY -> "facility";
      case RESOURCE -> "resource";
      default -> "resource";
    };
    assertEquals("facility", facility);
  }

}