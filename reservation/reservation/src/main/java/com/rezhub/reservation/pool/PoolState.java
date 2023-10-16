package com.rezhub.reservation.pool;

import lombok.With;

import java.util.HashSet;
import java.util.Set;

@With
public record PoolState(String poolId, String name, Set<String> resourceIds) {

    public static PoolState create(String poolId) {
        return new PoolState(poolId, "", new HashSet<>());
    }

    public PoolState withResourceId(String resourceId) {
        Set<String> ids = (resourceIds == null) ? new HashSet<>() : new HashSet<>(resourceIds);
        ids.add(resourceId);
        return new PoolState(poolId, name, ids);
    }

    public PoolState withoutResourceId(String resourceId) {
        Set<String> ids = new HashSet<>(resourceIds);
        ids.remove(resourceId);
        return new PoolState(poolId, name, ids);
    }
}
