package com.rezhub.reservation.actions;

import akka.Done;
import com.rezhub.reservation.dto.Reservation;
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
import java.util.Set;
import java.util.concurrent.CompletionStage;

@RequestMapping("/selection")
public class SelectionAction extends Action {

  private static final Logger log = LoggerFactory.getLogger(SelectionAction.class);
  public static final int TIMEOUT = 5;
  private final ComponentClient kalixClient;

  public SelectionAction(ComponentClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @SuppressWarnings("unused")
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  @PostMapping("/{reservationId}")
  public Effect<String> requestReservation(@RequestBody Selection selection, @PathVariable String reservationId) {
    log.info("SelectionAction requestReservation");
    var command = new ReservationEntity.Init(selection.reservation(), selection.resources());
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
        .thenApply(reservation -> reservation)
    );
  }

  static String timerName(String reservationId) {
    return "timer-" + reservationId;
  }

  public record Selection(Reservation reservation, Set<String> resources) {}
}
