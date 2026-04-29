package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceState;
import com.rezhub.reservation.resource.dto.Resource;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@HttpEndpoint("/resource")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ResourceEndpoint {

    private final ComponentClient componentClient;

    public ResourceEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/{resourceId}")
    public String createResource(String resourceId, Resource resource) {
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::create)
            .invoke(resource);
    }

    @Get("/{resourceId}")
    public ResourceState getResource(String resourceId) {
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::getResource)
            .invoke();
    }

    @Delete("/{resourceId}")
    public String deleteResource(String resourceId) {
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::deleteResource)
            .invoke();
    }

    /**
     * Weekly schedule body — wrapped record because Akka SDK cannot deserialize
     * raw generic types (Map&lt;DayOfWeek, List&lt;LocalTime&gt;&gt;) as endpoint body parameters.
     *
     * JSON shape: { "hours": { "MONDAY": ["14:00", "15:00"], "WEDNESDAY": ["09:00"] } }
     */
    public record ScheduleRequest(Map<String, List<String>> hours) {}

    /**
     * Set the weekly availability schedule for a resource.
     * Body: { "hours": { "MONDAY": ["14:00", "15:00"], "WEDNESDAY": ["09:00"] } }
     */
    @Put("/{resourceId}/schedule")
    public String setSchedule(String resourceId, ScheduleRequest body) {
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::setWeeklySchedule)
            .invoke(new ResourceEntity.WeeklyScheduleCommand(body.hours()));
    }

    /**
     * Set the resource type tag (e.g. "court", "player").
     */
    @Put("/{resourceId}/type")
    public String setResourceType(String resourceId, String resourceType) {
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::setResourceType)
            .invoke(resourceType);
    }

    /**
     * Set the external reference IDs pointing to the canonical records in other bounded contexts.
     */
    @Put("/{resourceId}/external-ref")
    public String setExternalRef(String resourceId, ResourceEntity.SetExternalRef command) {
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::setExternalRef)
            .invoke(command);
    }
}
