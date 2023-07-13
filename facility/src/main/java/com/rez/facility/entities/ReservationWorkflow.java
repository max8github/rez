package com.rez.facility.entities;

import com.rez.facility.actions.CalendarAction;
import com.rez.facility.domain.ReservationState;
import com.rez.facility.dto.Reservation;
import com.rez.facility.events.ReservationEvent;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.rez.facility.domain.ReservationState.State.*;

@TypeId("rez")
@Id("rezId")
@RequestMapping("/rez/{rezId}")
public class ReservationWorkflow extends Workflow<ReservationState> {
    private static final Logger log = LoggerFactory.getLogger(ReservationWorkflow.class);
    final private ComponentClient componentClient;

    public ReservationWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }


    @Override
    public WorkflowDef<ReservationState> definition() {
        Step search = step("search").call(RunSearch.class, c -> {
            var nextIndex = currentState().currentResourceIndex() + 1;
            if (currentState().resources().size() > nextIndex) {
                var nextResourceId = currentState().resources().get(nextIndex);
                var command = new ResourceEntity.InquireBooking(nextResourceId, c.reservationId(), c.facilityId(), c.reservation());
                return componentClient.forEventSourcedEntity(nextResourceId).call(ResourceEntity::inquireBooking).params(command);
            } else {
                var exhausted = new ReservationEvent.SearchExhausted(c.reservationId(), c.facilityId(), c.reservation(), currentState().resources());
                return componentClient.forAction().call(CalendarAction::unavailable).params(exhausted);
            }
        }).andThen(String.class, cmd -> {
            var nextIndex = currentState().currentResourceIndex() + 1;
            if (currentState().resources().size() > nextIndex) {
                return effects().updateState(currentState().withState(SELECTING).withIncrementedIndex()).pause();
            } else {
                return effects().updateState(currentState().withState(UNAVAILABLE).withIncrementedIndex()).end();
            }
        });


        Step book = step("book").call(Book.class, cmd -> {
            ReservationEvent.Booked booked = new ReservationEvent.Booked(cmd.resourceId(), cmd.reservationId(), cmd.reservation(), currentState().resources());
            return componentClient.forAction().call(CalendarAction::book).params(booked);
        }).andThen(String.class, cmd -> effects().updateState(currentState().withState(FULFILLED)).end());

        return workflow().addStep(search).addStep(book);
    }

    @PostMapping("/book")
    public Effect<String> book(@RequestBody Book command, @PathVariable String rezId) {
        return effects().transitionTo("book", command).thenReply("booking ...");
    }

    @PostMapping("/runSearch")
    public Effect<String> runSearch(@RequestBody RunSearch command, @PathVariable String rezId) {
        return effects().transitionTo("search", command).thenReply("keeping searching ...");
    }

    @PostMapping("/init")
    public Effect<String> init(@RequestBody InitiateReservation command, @PathVariable String rezId) {
        log.info("Created reservation {} entity", rezId);
        if (command.resources().isEmpty()) {
            return effects().error("there are no resources available to reserve");
        } else if (currentState() != null) {
            return effects().error("reservation process already started");
        } else {
            ReservationState initialState = new ReservationState(INIT, rezId, command.facilityId(),
                    command.reservation().emails(), command.reservation().timeSlot(), -1,
                    command.resources(), command.reservation().date());
            var cmd = new RunSearch(rezId, command.facilityId(), command.reservation());
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

    public record InitiateReservation(String reservationId, String facilityId, Reservation reservation,
                                      List<String> resources) {}

    public record RunSearch(String reservationId, String facilityId, Reservation reservation) {}

    public record Book(String resourceId, String reservationId, Reservation reservation) {}
}