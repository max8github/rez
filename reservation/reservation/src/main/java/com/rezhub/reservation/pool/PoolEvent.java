package com.rezhub.reservation.pool;

import com.rezhub.reservation.pool.dto.Pool;
import com.rezhub.reservation.resource.dto.Resource;
import com.rezhub.reservation.dto.Reservation;
import kalix.javasdk.annotations.TypeName;

import java.util.Set;

public sealed interface PoolEvent {

    @TypeName("pool-created")
    record Created(String entityId, Pool pool) implements PoolEvent {}

    @TypeName("pool-renamed")
    record Renamed(String newName) implements PoolEvent {}

    @TypeName("resource-submitted")
    record ResourceSubmitted(String poolId, Resource resourceDto, String resourceId) implements PoolEvent {}

    @TypeName("resource-id-added")
    record ResourceIdAdded(String resourceEntityId) implements PoolEvent {}

    @TypeName("resource-id-removed")
    record ResourceIdRemoved(String resourceEntityId) implements PoolEvent {}

    @TypeName("availability-requested")
    record AvalabilityRequested(String reservationId, Reservation reservation, Set<String> resources) implements PoolEvent {}

}