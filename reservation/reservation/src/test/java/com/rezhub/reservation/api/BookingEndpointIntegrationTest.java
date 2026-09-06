package com.rezhub.reservation.api;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.payment.PricingPolicy;
import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User Story 3 (T044) — the FR-012 gate on BookingEndpoint's direct HTTP path, closing the gap
 * research.md #10 found: this path carries no player identity, so only the facility-side check
 * applies here (unlike CourtBookingWorkflowIntegrationTest, which also covers FR-005).
 */
public class BookingEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void book_facilityWithPricingPolicyButNoConnectedAccount_returns400_noReservationCreated() throws Exception {
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::setPricingPolicy)
            .invoke(new PricingPolicy(5000, "eur", 0.10, Duration.ofDays(1)));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));
        // Deliberately no setStripeConnectedAccount call — onboarding incomplete.

        var request = new BookingEndpoint.BookingRequest(reservationId, LocalDateTime.now().plusDays(1),
            60, List.of("dave@example.com"), Set.of(resourceId), "recipient-x", "direct");

        var response = httpClient.POST("/bookings/")
            .withRequestBody(request)
            .invoke();

        assertThat(response.status().isSuccess()).isFalse();
        var state = componentClient.forEventSourcedEntity(reservationId).method(ReservationEntity::getReservation).invoke();
        assertThat(state.state()).isEqualTo(com.rezhub.reservation.reservation.ReservationState.State.INIT);
    }

    @Test
    public void book_facilityWithNoPricingPolicy_succeeds() throws Exception {
        String facilityId = "f_" + shortId();
        String resourceId = "court-" + shortId();
        String reservationId = "reservation-" + shortId();
        componentClient.forEventSourcedEntity(facilityId).method(FacilityEntity::create)
            .invoke(new Facility("Free Club", new Address("St", "City"), "Europe/Rome", null, null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::create)
            .invoke(new Resource(resourceId, "Court 1", null));
        componentClient.forEventSourcedEntity(resourceId).method(ResourceEntity::setExternalRef)
            .invoke(new ResourceEntity.SetExternalRef(resourceId, facilityId));

        var request = new BookingEndpoint.BookingRequest(reservationId, LocalDateTime.now().plusDays(1),
            60, List.of("erin@example.com"), Set.of(resourceId), "recipient-y", "direct");

        var response = httpClient.POST("/bookings/")
            .withRequestBody(request)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
