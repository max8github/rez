package com.rezhub.reservation.actions;

import com.rezhub.reservation.reservation.ReservationEntity;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@HttpEndpoint("/selection")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class RezAction {

  private static final Logger log = LoggerFactory.getLogger(RezAction.class);
  public static final int TIMEOUT = 14;
  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public RezAction(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  @Post("/{reservationId}")
  public ReservationEntity.ReservationId requestReservation(String reservationId, ReservationEntity.Init command) {
    log.info("---------- RezAction initiating reservation request {}", reservationId);

    // Register the timer first (before placing the reservation)
    timerScheduler.createSingleTimer(
      timerName(reservationId),
      Duration.ofSeconds(TIMEOUT),
      componentClient
        .forTimedAction()
        .method(TimerAction::expire)
        .deferred(reservationId)
    );

    // Now init the reservation
    return componentClient
      .forEventSourcedEntity(reservationId)
      .method(ReservationEntity::init)
      .invoke(command);
  }

  static String timerName(String reservationId) {
    return "timer-" + reservationId;
  }
}
