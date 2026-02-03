package com.rezhub.reservation.actions;

import com.rezhub.reservation.reservation.ReservationEntity;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "reservation-timer")
public class TimerAction extends TimedAction {

  private static final Logger log = LoggerFactory.getLogger(TimerAction.class);
  private final ComponentClient componentClient;

  public TimerAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect expire(String reservationId) {
    log.info("Expiring reservation '{}'", reservationId);
    try {
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
