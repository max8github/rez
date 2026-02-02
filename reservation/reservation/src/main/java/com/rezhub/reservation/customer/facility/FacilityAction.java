package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.rezhub.reservation.dto.Reservation.*;

@SuppressWarnings("unused")
@Component(id = "facility-events-consumer")
@Consume.FromEventSourcedEntity(FacilityEntity.class)
public class FacilityAction extends Consumer {
  private static final Logger log = LoggerFactory.getLogger(FacilityAction.class);
  private final ComponentClient kalixClient;

  public FacilityAction(ComponentClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @SuppressWarnings("unused")
  public Effect on(FacilityEvent.ResourceCreateAndRegisterRequested event) {
    var resourceId = event.resourceId();
    var command = new ResourceEntity.CreateChildResource(event.facilityId(), new Resource(event.resourceId(), event.resourceName()));
    var deferredCall = kalixClient.forEventSourcedEntity(resourceId).call(ResourceEntity::createFacilityResource).params(command);
    return effects().forward(deferredCall);
  }

  @SuppressWarnings("unused")
  public Effect on(FacilityEvent.AvalabilityRequested event) {
    log.info("Facility fans out, continuing the broadcast");
    CompletableFuture<Effect> completableFuture = broadcast(this, kalixClient,
      event.reservationId(), event.reservation(), event.resources());

    return effects().asyncEffect(completableFuture);
  }

  public static CompletableFuture<Effect> broadcast(Consumer consumer, ComponentClient kalixClient,
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
      ).thenApply(v -> consumer.effects().done());
  }

  public static String extractPrefix(String id) {
    int index = id.indexOf(DELIMITER);
    if (index > -1) {
      return id.substring(0, index) + DELIMITER;
    } else return "";
  }
}
