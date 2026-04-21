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

/**
 * @deprecated Legacy booking entry point used by the Telegram / BookingService path.
 * New callers should use {@code BookingEndpoint} (POST /bookings) which accepts a flat
 * set of resourceIds without any Facility or SelectionItem knowledge.
 * This endpoint and the FacilityEntity booking path it drives will be removed once
 * BookingService is migrated to resolve facility → resourceIds externally.
 */
@Deprecated
@HttpEndpoint("/selection")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class RezAction {

  private static final Logger log = LoggerFactory.getLogger(RezAction.class);
  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public RezAction(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  @Deprecated
  @Post("/{reservationId}")
  public ReservationEntity.ReservationId requestReservation(String reservationId, ReservationEntity.Init command) {
    log.info("---------- RezAction initiating reservation request {}", reservationId);

    timerScheduler.createSingleTimer(
      TimerAction.timerName(reservationId),
      Duration.ofSeconds(TimerAction.TIMEOUT_SECONDS),
      componentClient.forTimedAction().method(TimerAction::expire).deferred(reservationId)
    );

    return componentClient
      .forEventSourcedEntity(reservationId)
      .method(ReservationEntity::init)
      .invoke(command);
  }
}
