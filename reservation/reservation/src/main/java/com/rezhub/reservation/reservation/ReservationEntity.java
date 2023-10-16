package com.rezhub.reservation.reservation;

import akka.Done;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.resource.ResourceEntity;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.rezhub.reservation.reservation.ReservationState.State.*;
import static java.time.Duration.ofSeconds;

@TypeId("reservation")
@Id("reservationId")
@RequestMapping("/reservation/{reservationId}")
public class ReservationEntity extends Workflow<ReservationState> {
    private static final Logger log = LoggerFactory.getLogger(ReservationEntity.class);
    final private ComponentClient componentClient;

    public ReservationEntity(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }


    @SuppressWarnings("rawtypes")
    @Override
    public WorkflowDef<ReservationState> definition() {

        Step broadcast = step("broadcast").asyncCall(InitReservation.class, c -> {
            log.info("Broadcast starts to resources {}", c.resources());
            List<CompletableFuture<String>> futureList = c.resources().stream().sorted().map(id -> {
                var command = new ResourceEntity.CheckAvailability(c.reservationId(), c.facilityId(), c.reservation());
                return componentClient.forEventSourcedEntity(id).call(ResourceEntity::checkAvailability).params(command).execute().toCompletableFuture();
            }).toList();
            return CompletableFuture.allOf(futureList.toArray(new CompletableFuture<?>[0]))
              .thenApply(v -> futureList.stream()
                .map(CompletableFuture::join)
                .reduce("", (s, e) -> s + e)
              );//(Class<List<String>>) ((Class)List.class)
        }).andThen(String.class, __ -> effects()
          .updateState(currentState().withState(COLLECTING))
          .pause());

        Step reserve = step("reserve").call(ReplyAvailability.class, cmd -> {
            log.info("step reserve");
            ResourceEntity.Reserve command = new ResourceEntity.Reserve(
              cmd.reservationId,
              new Reservation(currentState().emails(), currentState().dateTime()),
              currentState().facilityId());
            return componentClient.forEventSourcedEntity(cmd.resourceId()).call(ResourceEntity::reserve).params(command);
        }).andThen(String.class, cmd -> effects().updateState(currentState()).pause());

        Step donothing = step("donothing")
          .asyncCall(() -> {
              log.info("step donothing");
              return CompletableFuture.completedStage("donothing");
          })
          .andThen(String.class, __ -> effects()
            .updateState(currentState())
            .pause());

        Step fulfill = step("fulfill").call(Fulfill.class, cmd -> {
            log.info("step fulfill {}", cmd);
            DelegatingServiceAction.Fulfilled fulfilled = new DelegatingServiceAction.Fulfilled(cmd.resourceId(), cmd.reservationId(),
              cmd.reservation(), currentState().resources(), currentState().facilityId());
            return componentClient.forAction().call(DelegatingServiceAction::book).params(fulfilled);
        }).andThen(String.class, cmd -> effects().updateState(currentState().withState(FULFILLED)).transitionTo("set-timer"));

        Step setTimer = step("set-timer")
          .asyncCall(() -> {
              log.info("step set-timer");
              String rezId = currentState().reservationId();
              return timers().startSingleTimer(
                "reservation-expiration-timer-" + rezId,
                Duration.ofSeconds(currentState().deadline() - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)),
                componentClient.forAction().call(ReservationAction::expire).params(rezId)
              ).toCompletableFuture();
          })
          .andThen(Done.class, cmd -> effects().pause());

        //COMPLETE is when a reservation is in the past. FULFILLED is when it is committed, but in the future (it can be cancelled)
        Step complete = step("complete")
          .asyncCall(() -> {
              log.info("step complete");
              return CompletableFuture.completedStage("complete");
          })
          .andThen(String.class, __ -> effects()
            .updateState(currentState().withState(COMPLETE))
            .end());

        Step unavailable = step("unavailable")
          .asyncCall(() -> {
              log.info("step unavailable");
              return CompletableFuture.completedStage("unavailable");
          })
          .andThen(String.class, __ -> effects()
            .updateState(currentState().withState(UNAVAILABLE))
            .end());

        Step cancelRequest = step("cancelRequest").call(Cancellation.class, cmd -> {
            log.info("step cancel request");
            return componentClient.forEventSourcedEntity(cmd.resourceId()).call(ResourceEntity::cancel).params(cmd.reservationId(), cmd.dateTime().toString());
        }).andThen(String.class, cmd -> effects().updateState(currentState().withState(CANCEL_REQUESTED)).pause());

        Step cancel = step("cancel").call(String.class, rezId -> {
            log.info("step cancel");
            String resourceId = getResourceIdFromState();
            List<String> resourceIds = currentState().resources();
            DelegatingServiceAction.Resources resources = new DelegatingServiceAction.Resources(resourceIds);
            return componentClient.forAction().call(DelegatingServiceAction::cancel).params(resourceId, rezId, resources);
        }).andThen(String.class, cmd -> effects().updateState(currentState().withState(CANCELLED)).transitionTo("complete"));

        return workflow()
          .addStep(broadcast)
          .addStep(fulfill)
          .addStep(reserve)
          .addStep(donothing)
          .addStep(cancelRequest)
          .addStep(cancel)
          .addStep(setTimer)
          .addStep(complete)
          .addStep(unavailable)
//                .timeout(ofSeconds(5))
          .defaultStepTimeout(ofSeconds(5));
    }

    @DeleteMapping("/cancelRequest")
    public Effect<String> cancelRequest(@PathVariable String reservationId) {
        Cancellation dto = new Cancellation(reservationId, getResourceIdFromState(), currentState().dateTime());
        return effects().transitionTo("cancelRequest", dto).thenReply("processing canceling ...");
    }
    private String getResourceIdFromState() {
        ReservationState state = currentState();
        return state.resourceId();
    }

    @DeleteMapping("/cancel")
    public Effect<String> cancel() {
        return effects().transitionTo("cancel", currentState().reservationId()).thenReply("canceling ...");
    }

    @PostMapping("/fulfill")
    public Effect<String> fulfill(@RequestBody Fulfill command, @PathVariable String reservationId) {
        if (currentState() == null || currentState().state() == null || currentState().reservationId() == null || currentState().reservationId().isEmpty()) {
            return effects().error("Reservation " + reservationId + " is not initialized, cannot be fulfilled.");
        }
        log.info("Reservation {} gets confirmation from resource {}", command.reservationId, command.resourceId());
        return effects().updateState(currentState().withRemoved(command.resourceId()).withResourceId(command.resourceId()).withState(FULFILLED))
          .transitionTo("fulfill", command).thenReply("OK");
    }

    @PostMapping("/runSearch")
    public Effect<String> runSearch(@RequestBody RunSearch command) {
        return effects().transitionTo("search", command).thenReply("keeping searching ...");
    }

    @DeleteMapping("/complete")
    public Effect<String> complete() {
        return effects().transitionTo("complete").thenReply("reservation completed.");
    }

    @PostMapping("/init")
    public Effect<String> init(@RequestBody Init command, @PathVariable String reservationId) {
        log.info("ReservationEntity initializes with reservation id {}", reservationId);
        if (command.resources().isEmpty()) {
            return effects().error("There are no resources available to reserve");
        } else if (currentState() != null) {
            return effects().error("Reservation already initialized. State: " + currentState().state());
        } else {
            Reservation reservation = command.reservation();
            ReservationState initialState = ReservationState.initiate(reservationId).withState(COLLECTING).withFacilityId(command.facilityId())
              .withEmails(reservation.emails()).withResources(command.resources()).withDateTime(reservation.dateTime());

            var cmd = new InitReservation(reservationId, command.facilityId, command.reservation, command.resources);
            return effects().updateState(initialState).transitionTo("broadcast", cmd).thenReply("processing reservation ...");
        }
    }

    @PostMapping("/replyAvailability")
    public Effect<String> replyAvailability(@RequestBody ReplyAvailability command) {
        if(command.available) {
            if(currentState().state().equals(COLLECTING)) {
                return effects().updateState(currentState().withAdded(command.resourceId()).withState(SELECTING))
                  .transitionTo("reserve", command).thenReply("OK");
            } else {
                return effects().updateState(currentState().withAdded(command.resourceId()))
                  .transitionTo("donothing").thenReply("OK");
            }
        } else {
            return effects().reply("OK");
        }
    }

    @PostMapping("/expire")
    public Effect<String> expire()  {
        log.info("Reservation {} is set to expire for timeout", currentState().reservationId());
        return effects().updateState(currentState().withState(UNAVAILABLE)).transitionTo("unavailable").thenReply("reservation completed as unavailable.");
    }

    @PostMapping("/reject")
    public Effect<String> reject(@RequestBody Reject command) {
        log.info("Reservation {} was rejected, {}, even after replying as available", currentState().reservationId(), command);
        if(currentState().state().equals(SELECTING)) {
            if(currentState().hasAvailableResources()) {
                String nextResourceId = currentState().pop();
                ReplyAvailability cmd = new ReplyAvailability(currentState().reservationId(), nextResourceId, true, currentState().facilityId());
                return effects().updateState(currentState().withRemoved(command.resourceId()).withResourceId(nextResourceId)).transitionTo("reserve", cmd).thenReply("rejected reservation ...");
            } else {
                return effects().updateState(currentState().withRemoved(command.resourceId()).withState(COLLECTING)).transitionTo("donothing").thenReply("rejected reservation ...");
            }
        } else
            return effects().updateState(currentState().withState(COLLECTING)).transitionTo("donothing").thenReply("rejected reservation ...");
    }

    @GetMapping
    public Effect<ReservationState> getState() {
        if (currentState() == null) {
            return effects().error("reservation not started");
        } else {
            return effects().reply(currentState());
        }
    }

    public record Init(String facilityId, Reservation reservation, List<String> resources) {}
    public record InitReservation(String reservationId, String facilityId, Reservation reservation,
                                  List<String> resources) {}

    public record RunSearch(String reservationId, String facilityId, Reservation reservation) {}
    public record ReplyAvailability(String reservationId, String resourceId, boolean available, String facilityId) {}
    public record Fulfill(String resourceId, String reservationId, Reservation reservation, String facilityId) {}
    public record Reject(String resourceId) {}
    public record Cancellation(String reservationId, String resourceId, LocalDateTime dateTime) {}
}