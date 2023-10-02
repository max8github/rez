package com.rez.facility.reservation;

import akka.Done;
import com.rez.facility.dto.Reservation;
import com.rez.facility.resource.ResourceEntity;
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

import static com.rez.facility.reservation.ReservationState.State.*;
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
        Step search = step("search").call(RunSearch.class, c -> {
            var nextIndex = currentState().currentResourceIndex() + 1;
            if (nextIndex < currentState().resources().size()) {
                var nextResourceId = currentState().resources().get(nextIndex);
                log.info("step search, nextResourceId: {}", nextResourceId);
                var command = new ResourceEntity.InquireBooking(c.reservationId(), c.facilityId(), c.reservation());
                return componentClient.forEventSourcedEntity(nextResourceId).call(ResourceEntity::inquireBooking).params(command);
            } else {
                log.info("step search, exhausted");
                var exhausted = new DelegatingServiceAction.NotifySearchExhausted(c.reservationId(), c.facilityId(), c.reservation(), currentState().resources());
                return componentClient.forAction().call(DelegatingServiceAction::unavailable).params(exhausted);
            }
        }).andThen(String.class, cmd -> {
            var nextIndex = currentState().currentResourceIndex() + 1;
            if (nextIndex < currentState().resources().size()) {
                log.info("step search, transitioning, still SELECTING");
                return effects().updateState(currentState().withState(SELECTING).withIncrementedIndex()).pause();
            } else {
                log.info("step search, exiting, UNAVAILABLE");
                return effects().updateState(currentState().withState(UNAVAILABLE)).end();
            }
        });

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
                        .end())
                .timeout(ofSeconds(1));

        Step book = step("book").call(Book.class, cmd -> {
            log.info("step book");
            DelegatingServiceAction.Booked booked = new DelegatingServiceAction.Booked(cmd.resourceId(), cmd.reservationId(),
                    cmd.reservation(), currentState().resources(), currentState().facilityId());
            return componentClient.forAction().call(DelegatingServiceAction::book).params(booked);
        }).andThen(String.class, cmd -> effects().updateState(currentState().withState(FULFILLED)).transitionTo("set-timer"));

        Step cancelRequest = step("cancelRequest").call(CancellationDto.class, cmd -> {
            log.info("step cancel request");
            return componentClient.forEventSourcedEntity(cmd.resourceId()).call(ResourceEntity::cancel).params(cmd.reservationId(), cmd.dateTime().toString());
        }).andThen(String.class, cmd -> effects().updateState(currentState().withState(CANCEL_REQUESTED)).pause());

        Step cancel = step("cancel").call(String.class, rezId -> {
            log.info("step cancel");
            String resourceId = getResourceIdFromState();
            List<String> resourceIds = currentState().resources();
            DelegatingServiceAction.Resources resources = new DelegatingServiceAction.Resources(resourceIds);
            return componentClient.forAction().call(DelegatingServiceAction::cancel).params(resourceId, rezId, resources);
        }).andThen(String.class, cmd -> effects().updateState(currentState().withIncrementedIndex().withState(CANCELLED)).transitionTo("complete"));

        return workflow()
                .addStep(search)
                .addStep(book)
                .addStep(cancelRequest)
                .addStep(cancel)
                .addStep(setTimer)
                .addStep(complete);
    }

    @DeleteMapping("/cancelRequest")
    public Effect<String> cancelRequest(@PathVariable String reservationId) {
        CancellationDto dto = new CancellationDto(reservationId, getResourceIdFromState(), currentState().dateTime());
        return effects().transitionTo("cancelRequest", dto).thenReply("processing canceling ...");
    }
    private String getResourceIdFromState() {
        ReservationState state = currentState();
        return state.resources().get(state.currentResourceIndex());
    }

    @DeleteMapping("/cancel")
    public Effect<String> cancel() {
        return effects().transitionTo("cancel", currentState().reservationId()).thenReply("canceling ...");
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody Book command) {
        return effects().transitionTo("book", command).thenReply("booking ...");
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
    public Effect<String> init(@RequestBody InitiateReservation command, @PathVariable String reservationId) {
        log.info("Created reservation {} entity", reservationId);
        if (command.resources().isEmpty()) {
            return effects().error("There are no resources available to reserve");
        } else if (currentState() != null) {
            return effects().error("Reservation already initialized. State: " + currentState().state());
        } else {
            ReservationState initialState = new ReservationState(INIT, reservationId, command.facilityId(),
                    command.reservation().emails(), -1,
                    command.resources(), command.reservation().dateTime());
            var cmd = new RunSearch(reservationId, command.facilityId(), command.reservation());
            return effects().updateState(initialState).transitionTo("search", cmd).thenReply("processing reservation ...");
        }
    }

    @GetMapping
    public Effect<ReservationState> getState() {
        if (currentState() == null) {
            return effects().error("reservation not started");
        } else {
            return effects().reply(currentState());
        }
    }

    public record InitiateReservation(String facilityId, Reservation reservation,
                                      List<String> resources) {}

    public record RunSearch(String reservationId, String facilityId, Reservation reservation) {}

    public record Book(String resourceId, String reservationId, Reservation reservation, String facilityId) {}
    public record CancellationDto(String reservationId, String resourceId, LocalDateTime dateTime) {}
}