package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
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

    /**
     * Set the weekly availability schedule for a resource.
     * Body: map of day-of-week to list of bookable start hours (full hours only).
     * Example: { "MONDAY": ["14:00", "15:00", "16:00"], "WEDNESDAY": ["09:00"] }
     * An empty map clears the schedule (all hours become bookable again).
     */
    @Put("/{resourceId}/schedule")
    public String setSchedule(String resourceId, Map<DayOfWeek, List<LocalTime>> body) {
        Map<DayOfWeek, Set<LocalTime>> schedule = body.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
        return componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::setWeeklySchedule)
            .invoke(schedule);
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
