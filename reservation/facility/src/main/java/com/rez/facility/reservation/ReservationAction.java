package com.rez.facility.reservation;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@SuppressWarnings("unused")
@RequestMapping("/rezmanage")
public class ReservationAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(ReservationAction.class);
    private final ComponentClient componentClient;

    public ReservationAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @DeleteMapping("/{rezId}")
    public Effect<String> expire(@PathVariable String rezId) {
        log.info("called expire with id {}", rezId);
        var deferredCall = componentClient.forWorkflow(rezId).call(ReservationEntity::complete);
        return effects().forward(deferredCall);
    }
}
