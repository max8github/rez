package com.rezhub.reservation.resource;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.rezhub.reservation.resource.dto.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceEntityTest {

    private static final String CALENDAR_ID = "abc123@group.calendar.google.com";
    private static final String FACILITY_ID = "f_club1";
    private static final String RESOURCE_ID = "r_court1";

    @Test
    void createFacilityResource_storesCalendarIdOnState() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);
        var command = new ResourceEntity.CreateChildResource(
            FACILITY_ID,
            new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        var result = testKit.method(ResourceEntity::createFacilityResource).invoke(command);

        var event = result.getNextEventOfType(ResourceEvent.FacilityResourceCreated.class);
        assertThat(event.calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(event.parentId()).isEqualTo(FACILITY_ID);
        assertThat(event.name()).isEqualTo("Court 1");

        assertThat(testKit.getState().calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(testKit.getState().name()).isEqualTo("Court 1");
    }

    @Test
    void createResource_storesCalendarIdOnState() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "Court 1", CALENDAR_ID));

        var event = result.getNextEventOfType(ResourceEvent.ResourceCreated.class);
        assertThat(event.calendarId()).isEqualTo(CALENDAR_ID);

        assertThat(testKit.getState().calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(testKit.getState().name()).isEqualTo("Court 1");
    }

    @Test
    void createResource_withNullCalendarId_isValid() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "Court 1", null));

        assertThat(result.isError()).isFalse();
        assertThat(testKit.getState().calendarId()).isNull();
    }

    @Test
    void createResource_rejectsEmptyName() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, "", CALENDAR_ID));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void createResource_rejectsForbiddenName() {
        var testKit = EventSourcedTestKit.of(RESOURCE_ID, ResourceEntity::new);

        var result = testKit.method(ResourceEntity::create)
            .invoke(new Resource(RESOURCE_ID, Resource.FORBIDDEN_NAME, CALENDAR_ID));

        assertThat(result.isError()).isTrue();
    }
}
