package com.rezhub.reservation;

import akka.javasdk.testkit.TestKitSupport;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceState;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the reservation system using Akka 3 TestKitSupport.
 * These tests verify the interaction between components through the ComponentClient.
 */
public class ReservationIntegrationTest extends TestKitSupport {

    @Test
    public void testCreateFacility() {
        String facilityId = "f_test-facility";
        Address address = new Address("Test Street 123", "Test City");
        Facility facility = new Facility("Test Facility", address, Collections.emptySet());

        // Create the facility
        String result = componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(facility);

        assertEquals(facilityId, result);

        // Verify the facility was created
        Facility state = componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getFacility)
            .invoke();

        assertEquals("Test Facility", state.name());
        assertNotNull(state.address());
    }

    @Test
    public void testCreateResource() {
        String resourceId = "resource-test-1";
        Resource resource = new Resource(resourceId, "Tennis Court 1");

        // Create the resource
        String result = componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::create)
            .invoke(resource);

        assertEquals(resourceId, result);

        // Verify the resource was created
        ResourceState state = componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::getResource)
            .invoke();

        assertEquals("Tennis Court 1", state.name());
    }

    @Test
    public void testFacilityWithResource() {
        String facilityId = "f_facility-with-resource";
        String resourceId = "resource-for-facility";

        // Create the facility
        Address address = new Address("Main Street", "Berlin");
        Facility facility = new Facility("Tennis Club", address, Collections.emptySet());

        componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(facility);

        // Register a resource to the facility
        String registeredId = componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::registerResource)
            .invoke(resourceId);

        assertEquals(resourceId, registeredId);

        // Verify the resource is registered
        Facility facilityState = componentClient
            .forEventSourcedEntity(facilityId)
            .method(FacilityEntity::getFacility)
            .invoke();

        assertTrue(facilityState.resourceIds().contains(resourceId));
    }
}
