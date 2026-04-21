package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.dto.EntityType;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.dto.SelectionItem;
import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component(id = "facility-events-consumer")
@Consume.FromEventSourcedEntity(value = FacilityEntity.class, ignoreUnknown = true)
public class FacilityAction extends Consumer {
    private static final Logger log = LoggerFactory.getLogger(FacilityAction.class);
    private final ComponentClient componentClient;

    public FacilityAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect on(FacilityEvent.ResourceCreateAndRegisterRequested event) {
        var resourceId = event.resourceId();
        var command = new ResourceEntity.CreateChildResource(event.facilityId(), new Resource(event.resourceId(), event.resourceName(), event.calendarId()));
        componentClient
            .forEventSourcedEntity(resourceId)
            .method(ResourceEntity::createFacilityResource)
            .invoke(command);
        return effects().done();
    }

    /**
     * @deprecated Legacy booking fan-out. Facility availability requests will be removed
     * once BookingService resolves facility → resourceIds externally.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Effect on(FacilityEvent.AvalabilityRequested event) {
        log.info("Facility fans out, continuing the broadcast");
        Set<SelectionItem> items = FacilityState.normalizeResourceIds(event.resources()).stream()
            .map(id -> new SelectionItem(id, EntityType.RESOURCE))
            .collect(Collectors.toUnmodifiableSet());
        if (items.isEmpty()) {
            log.warn("Facility availability request {} has no resources to fan out to", event.reservationId());
            return effects().done();
        }
        broadcast(componentClient, event.reservationId(), event.reservation(), items);
        return effects().done();
    }

    /**
     * @deprecated Legacy broadcast used by the /selection endpoint path. New bookings go
     * through {@code BookingEndpoint} which calls resources directly via flat resourceIds.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static void broadcast(ComponentClient componentClient,
                                 String reservationId, Reservation reservation,
                                 Set<SelectionItem> items) {
        items.stream().sorted(java.util.Comparator.comparing(SelectionItem::id)).forEach(item -> {
            var command = new ResourceEntity.CheckAvailability(reservationId, reservation);
            switch (item.type()) {
                case FACILITY -> componentClient
                    .forEventSourcedEntity(item.id())
                    .method(FacilityEntity::checkAvailability)
                    .invokeAsync(command)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Error checking availability for facility {}: {}", item.id(), error.getMessage());
                        }
                    });
                case RESOURCE -> componentClient
                    .forEventSourcedEntity(item.id())
                    .method(ResourceEntity::checkAvailability)
                    .invokeAsync(command)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Error checking availability for resource {}: {}", item.id(), error.getMessage());
                        }
                    });
            }
        });
    }
}
