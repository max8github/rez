package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.rezhub.reservation.dto.Reservation.*;

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
    CompletableFuture<Effect<String>> completableFuture = broadcast(this, kalixClient,
      event.reservationId(), event.reservation(), event.resources());

    return effects().asyncEffect(completableFuture);
  }

  public static CompletableFuture<Effect<String>> broadcast(Action action, ComponentClient kalixClient,
                                                            String reservationId, Reservation reservation,
                                                            Set<String> resources) {
    List<CompletableFuture<String>> futureChecks = resources.stream().sorted().map(id -> {
      var command = new ResourceEntity.CheckAvailability(reservationId, reservation);
      //Note: cannot use inheritance. If it were possible, checkAvailability() would
      //be a method (of a super entity) with polymorphic behavior.
      String type = extractPrefix(id);
      return switch (type) {
        case FACILITY -> kalixClient.forEventSourcedEntity(id).call(FacilityEntity::checkAvailability).params(command).execute().toCompletableFuture();
        case RESOURCE -> kalixClient.forEventSourcedEntity(id).call(ResourceEntity::checkAvailability).params(command).execute().toCompletableFuture();
        default -> kalixClient.forEventSourcedEntity(id).call(ResourceEntity::checkAvailability).params(command).execute().toCompletableFuture();
//                default -> throw new IllegalStateException("Unexpected value: " + type);
      };

    }).toList();

    return CompletableFuture.allOf(futureChecks.toArray(new CompletableFuture<?>[0]))
      .thenApply(v -> futureChecks.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList())
      ).thenApply(v -> action.effects().reply("ok - broadcast"));
  }

  public static String extractPrefix(String id) {
    int index = id.indexOf(DELIMITER);
    if (index > -1) {
      return id.substring(0, index) + DELIMITER;
    } else return "";
  }
}
