package com.mcalder.recordhub.customer.resource;

import kalix.javasdk.annotations.TypeName;

public sealed interface ResourceEvent {

  @TypeName("resource-created")
  record ResourceCreated(String resourceId, String resourceName, String facilityId) implements ResourceEvent {}
}