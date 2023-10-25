package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.actions.ReservationAction;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@Subscribe.EventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(FacilityAction.class);
  private final ComponentClient kalixClient;

  public FacilityAction(ComponentClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @SuppressWarnings("unused")
  public Effect<String> on(FacilityEvent.ResourceCreateAndRegisterRequested event) {
    var resourceId = event.resourceId();
    var command = new ResourceEntity.CreateChildResource(event.facilityId(), new Resource(event.resourceId(), event.resourceName()));
    var deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::createFacilityResource).params(command);
    return effects().forward(deferredCall);
  }

  @SuppressWarnings("unused")
  public Effect<String> on(FacilityEvent.AvalabilityRequested event) {
    log.info("fan out, continue the broadcast");
    CompletableFuture<Effect<String>> completableFuture = ReservationAction.broadcast(this, kalixClient,
      event.reservationId(), event.reservation(), event.resources());

    return effects().asyncEffect(completableFuture);
  }
}
