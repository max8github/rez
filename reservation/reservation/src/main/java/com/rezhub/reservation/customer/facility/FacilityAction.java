package com.rezhub.reservation.customer.facility;

import com.rezhub.reservation.resource.ResourceEntity;
import com.rezhub.reservation.resource.dto.Resource;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

}
