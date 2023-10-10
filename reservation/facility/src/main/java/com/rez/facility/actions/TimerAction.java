package com.rez.facility.actions;

import com.rez.facility.reservation.ReservationEntity;
import kalix.javasdk.DeferredCallResponseException;
import kalix.javasdk.StatusCode;
import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;
import java.util.concurrent.CompletionStage;

@RequestMapping("/timer")
public class TimerAction extends Action {

    private static final Logger log = LoggerFactory.getLogger(TimerAction.class);
    private final ComponentClient kalixClient;

    public TimerAction(ComponentClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    @PostMapping("/{reservationId}")
    public Action.Effect<String> expire(@PathVariable String reservationId) {
        log.info("Expiring reservation '{}'", reservationId);
        var expireRequest = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::expire);

        CompletionStage<String> reply =
                expireRequest
                        .execute()
                        .thenApply(cancelled -> "Ok")
                        .exceptionally(e -> {
                                    if (e.getCause() instanceof DeferredCallResponseException dcre &&
                                            Set.of(StatusCode.ErrorCode.NOT_FOUND, StatusCode.ErrorCode.BAD_REQUEST).contains(dcre.errorCode())) {
                                        // if NotFound or InvalidArgument, we don't need to re-try, and we can move on
                                        // other kind of failures are not recovered and will trigger a re-try
                                        return "Ok";
                                    } else {
                                        throw new RuntimeException(e);
                                    }
                                }
                        );
        return effects().asyncReply(reply);
    }
}
