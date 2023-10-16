package com.rezhub.reservation.view;

import com.rezhub.reservation.pool.PoolEntity;
import com.rezhub.reservation.pool.PoolEvent;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import reactor.core.publisher.Flux;

import org.springframework.web.bind.annotation.GetMapping;

@ViewId("view_pools_by_name")
@Table("pools_by_name")
public class PoolByNameView extends View<PoolV> {

    @SuppressWarnings("unused")
    @GetMapping("/pool/by_name/{pool_name}")
    @Query("SELECT * FROM pools_by_name WHERE name = :pool_name")
    public Flux<PoolV> getPool(String name) {
        return null;
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(PoolEntity.class)
    public UpdateEffect<PoolV> onEvent(PoolEvent.Created created) {
        return effects().updateState(new PoolV(
          created.pool().name(),
          created.entityId()));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(PoolEntity.class)
    public UpdateEffect<PoolV> onEvent(PoolEvent.Renamed event) {
        return effects().updateState(viewState().withName(event.newName()));
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(PoolEntity.class)
    public UpdateEffect<PoolV> onEvent(PoolEvent.ResourceSubmitted event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(PoolEntity.class)
    public UpdateEffect<PoolV> onEvent(PoolEvent.ResourceIdAdded event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(PoolEntity.class)
    public UpdateEffect<PoolV> onEvent(PoolEvent.ResourceIdRemoved event) {
        return effects().ignore();
    }

    @SuppressWarnings("unused")
    @Subscribe.EventSourcedEntity(PoolEntity.class)
    public UpdateEffect<PoolV> onEvent(PoolEvent.AvalabilityRequested event) {
        return effects().ignore();
    }
}