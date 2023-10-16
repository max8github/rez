package com.rezhub.reservation.pool;

import com.rezhub.reservation.pool.dto.Pool;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import kalix.javasdk.annotations.*;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Id("poolId")
@TypeId("pool")
@RequestMapping("/pool/{poolId}")
public class PoolEntity extends EventSourcedEntity<PoolState, PoolEvent> {
    private static final Logger log = LoggerFactory.getLogger(PoolEntity.class);
    public static final String PREFIX = "pool-";
    private final String entityId;

    public PoolEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public PoolState emptyState() {
        return PoolState.create(entityId).withName("noname");
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/create")
    public Effect<String> create(@RequestBody Pool pool) {
        String id = commandContext().entityId();
        log.info("creating pool {} with id {}", pool.name(), id);
        if(id.startsWith(PREFIX) || id.startsWith("stub")) {
            return effects()
              .emitEvent(new PoolEvent.Created(id, pool))
              .thenReply(newState -> id);
        } else {
            String message = "The id provided, '" + id + "', is not valid for a Pool: it must start with the prefix '" + PREFIX + "' (or 'stub' for tests)";
            log.error(message);
            return effects().error(message);
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public PoolState created(PoolEvent.Created created) {
        var dto = created.pool();
        return PoolState.create(created.entityId())
                .withName(dto.name())
                .withResourceIds(dto.resourceIds());
    }

    @PostMapping("/rename/{newName}")
    public Effect<String> rename(@PathVariable String newName) {
        return effects()
                .emitEvent(new PoolEvent.Renamed(newName))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public PoolState renamed(PoolEvent.Renamed renamed) {
        return currentState().withName(renamed.newName());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping("/resource/submit")
    public Effect<String> submitResource(@RequestBody Resource resourceDto) {
        String id = resourceDto.resourceId();
        return effects()
                .emitEvent(new PoolEvent.ResourceSubmitted(currentState().poolId(), resourceDto, id))
                .thenReply(newState -> id);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public PoolState resourceIdSubmitted(PoolEvent.ResourceSubmitted event) {
        return currentState();
    }

    @PostMapping("/resource/{resourceId}")
    public Effect<String> addResourceId(@PathVariable String resourceId) {
        log.info("added resource id {}", resourceId);
        return effects()
                .emitEvent(new PoolEvent.ResourceIdAdded(resourceId))
                .thenReply(newState -> resourceId);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public PoolState resourceIdAdded(PoolEvent.ResourceIdAdded event) {
        return currentState().withResourceId(event.resourceEntityId());
    }

    @DeleteMapping("/resource/{resourceId}")
    public Effect<String> removeResourceId(@PathVariable String resourceId) {
        if (!currentState().resourceIds().contains(resourceId)) {
            return effects().error("Cannot remove resource " + resourceId + " because it is not in the pool.");
        }
        return effects()
                .emitEvent(new PoolEvent.ResourceIdRemoved(resourceId))
                .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public PoolState resourceIdRemoved(PoolEvent.ResourceIdRemoved event) {
        return currentState().withoutResourceId(event.resourceEntityId());
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<Pool> getPool() {
        PoolState poolState = currentState();
        return effects().reply(new Pool(poolState.name(), poolState.resourceIds()));
    }

    @PostMapping("/checkAvailability")
    public Effect<String> checkAvailability(@RequestBody ResourceEntity.CheckAvailability command) {
        log.info("PoolEntity {} checks availability for reservation {} by delegating to its resources", entityId, command.reservation());
        return effects()
          .emitEvent(new PoolEvent.AvalabilityRequested(command.reservationId(), command.reservation(), currentState().resourceIds()))
          .thenReply(newState -> "OK");
    }

    @SuppressWarnings("unused")
    @EventHandler
    public PoolState availabilityChecked(PoolEvent.AvalabilityRequested event) {
        return currentState();
    }
}