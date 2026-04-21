package com.rezhub.reservation.actions;

import com.rezhub.reservation.reservation.ReservationEntity;
import com.rezhub.reservation.reservation.ReservationState;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import akka.javasdk.timedaction.TimedAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Component(id = "reservation-timer")
public class TimerAction extends TimedAction {

  public static final int TIMEOUT_SECONDS = 14;

  public static String timerName(String reservationId) {
    return "timer-" + reservationId;
  }

  private static final Logger log = LoggerFactory.getLogger(TimerAction.class);
  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public TimerAction(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  public Effect expire(String reservationId) {
    log.info("Expiring reservation '{}'", reservationId);
    try {
      ReservationState state = componentClient
        .forEventSourcedEntity(reservationId)
        .method(ReservationEntity::getReservation)
        .invoke();

      if (state.state() == ReservationState.State.SELECTING) {
        // A resource lock attempt is in flight. Do not expire now — the ResourceAction
        // will either fulfill (cancelling this timer name) or the compensation will
        // release the lock. Re-arm a short watchdog so we can still expire if the
        // resource later rejects and the reservation returns to COLLECTING.
        log.info("Reservation '{}' in SELECTING when timer fired — re-arming watchdog", reservationId);
        timerScheduler.createSingleTimer(
          timerName(reservationId),
          Duration.ofSeconds(3),
          componentClient.forTimedAction().method(TimerAction::expire).deferred(reservationId)
        );
        return effects().done();
      }

      componentClient
        .forEventSourcedEntity(reservationId)
        .method(ReservationEntity::expire)
        .invoke();
      return effects().done();
    } catch (IllegalArgumentException e) {
      // NotFound or InvalidArgument - we don't need to re-try
      log.info("Reservation '{}' not found or invalid, timer completed", reservationId);
      return effects().done();
    } catch (Exception e) {
      // Other failures should trigger a re-try
      log.error("Error expiring reservation '{}': {}", reservationId, e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
