package com.rezhub.reservation.actions;

import akka.Done;
import com.rezhub.reservation.reservation.ReservationEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@RequestMapping("/selection")
public class RezAction extends Action {

  private static final Logger log = LoggerFactory.getLogger(RezAction.class);
  public static final int TIMEOUT = 10;
  private final ComponentClient kalixClient;

  public RezAction(ComponentClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @SuppressWarnings("unused")
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/{reservationId}")
  public Effect<ReservationEntity.ReservationId> requestReservation(@RequestBody ReservationEntity.Init command, @PathVariable String reservationId) {
    log.info("RezAction requestReservation");

    CompletionStage<Done> timerRegistration =
      timers().startSingleTimer(
        timerName(reservationId),
        Duration.ofSeconds(TIMEOUT),
        kalixClient.forAction().call(TimerAction::expire).params(reservationId)
      );
    var request = kalixClient.forEventSourcedEntity(reservationId).call(ReservationEntity::init).params(command);
    return effects().asyncReply(
      timerRegistration
        .thenCompose(done -> request.execute())
        .thenApply(response -> response)
    );
  }

  static String timerName(String reservationId) {
    return "timer-" + reservationId;
  }
}
