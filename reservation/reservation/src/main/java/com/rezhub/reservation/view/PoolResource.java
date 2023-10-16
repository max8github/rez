package com.rezhub.reservation.view;

public record PoolResource(
        String name,
        String poolId,
        String resourceName,
        String resourceId,
        String[] timeWindow
        ) {}
