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
@Consume.FromEventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(FacilityAction.class);
    private final ComponentClient componentClient;

    public FacilityAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @SuppressWarnings("unused")
    public Effect on(FacilityEvent.ResourceCreateAndRegisterRequested event) {
        var resourceId = event.resourceId();
        var command = new ResourceEntity.CreateChildResource(event.facilityId(), new Resource(event.resourceId(), event.resourceName()));
        componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::createFacilityResource)
            .invoke(command);
        return effects().done();
    }

    @SuppressWarnings("unused")
    public Effect on(FacilityEvent.AvalabilityRequested event) {
        log.info("Facility fans out, continuing the broadcast");
        broadcast(componentClient, event.reservationId(), event.reservation(), event.resources());
        return effects().done();
    }

    public static void broadcast(ComponentClient componentClient,
                                 String reservationId, Reservation reservation,
                                 Set<String> resources) {
        resources.stream().sorted().forEach(id -> {
            var command = new ResourceEntity.CheckAvailability(reservationId, reservation);
            //Note: cannot use inheritance. If it were possible, checkAvailability() would
            //be a method (of a super entity) with polymorphic behavior.
            String type = extractPrefix(id);
            switch (type) {
                case FACILITY -> componentClient
                    .forEventSourcedEntity(id)
                    .method(FacilityEntity::checkAvailability)
                    .invokeAsync(command)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Error checking availability for facility {}: {}", id, error.getMessage());
                        }
                    });
                case RESOURCE -> componentClient
                    .forEventSourcedEntity(id)
                    .method(ResourceEntity::checkAvailability)
                    .invokeAsync(command)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Error checking availability for resource {}: {}", id, error.getMessage());
                        }
                    });
                default -> componentClient
                    .forEventSourcedEntity(id)
                    .method(ResourceEntity::checkAvailability)
                    .invokeAsync(command)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Error checking availability for {}: {}", id, error.getMessage());
                        }
                    });
            }
        });
    }

    public static String extractPrefix(String id) {
        int index = id.indexOf(DELIMITER);
        if (index > -1) {
            return id.substring(0, index) + DELIMITER;
        } else return "";
    }
}
