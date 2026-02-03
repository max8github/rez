package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.ResourceState;
import com.rezhub.reservation.resource.dto.Resource;

/**
 * HTTP endpoint for Resource operations.
 */
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
}
