package com.rezhub.customer.resource;

import com.rezhub.customer.resource.dto.Resource;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Id("resource_id")
@TypeId("resource")
@RequestMapping("/resource/{resource_id}")
public class ResourceEntity extends EventSourcedEntity<ResourceState, ResourceEvent> {
  private static final Logger log = LoggerFactory.getLogger(ResourceEntity.class);
  private final String entityId;

  public ResourceEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public ResourceState emptyState() {
    return new ResourceState("noname", entityId);
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateResourceCommand resCommand) {
    String resourceName = resCommand.resourceDto().resourceName();
    return effects()
      .emitEvent(new ResourceEvent.ResourceCreated(entityId, resourceName, resCommand.facilityId()))
      .thenReply(newState -> "OK - " + resourceName);
  }

  @SuppressWarnings("unused")
  @EventHandler
  public ResourceState created(ResourceEvent.ResourceCreated resourceCreated) {
    return new ResourceState(resourceCreated.resourceName(), resourceCreated.resourceName());
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @GetMapping()
  public Effect<ResourceState> getResource() {
    return effects().reply(currentState());
  }

  public record CreateResourceCommand(String facilityId, Resource resourceDto) {}

}