package com.rezhub.reservation.actions;

import com.rezhub.reservation.spi.Assembler;
import com.rezhub.reservation.spi.Parser;
import com.rezhub.reservation.dto.Reservation;
import com.rezhub.reservation.reservation.ReservationEntity;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@HttpEndpoint("/outwebhook")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class WebhookAction extends AbstractHttpEndpoint {
    private static final Logger log = LoggerFactory.getLogger(WebhookAction.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;
    private final Assembler assembler;
    private final Parser parser;

    public WebhookAction(ComponentClient componentClient, TimerScheduler timerScheduler, Assembler assembler, Parser parser) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
        this.assembler = assembler;
        this.parser = parser;
    }

    /**
     * This is the input to rez. It is also called "outgoing webhook" from Twist's standpoint: Twist -to-> Akka.
     * It is used for initiating the entire processing.
     * Posting a message in the set-up Twist thread triggers the sending of that message to Akka, caught here.
     *
     * The action does two things: upon receiving a generic Json object, it first translates that into its appropriate
     * object based on implementor (as there are different text message providers, like Twist).
     * It then interprets what its content says by calling a parser.
     * @return message back to the Text Message provider (Twist), optionally
     */
    @Post
    public CompletionStage<Parser.Text> outwebhook(com.fasterxml.jackson.databind.JsonNode blob) {
        Parser.TextMessage textMessage = assembler.assemble(blob);
        String facilityId = textMessage.thread_id(); // thread_id must be the same as the facility id (todo: provisioning).

        if (textMessage.system_message() != null) { // drop it
            log.info("dropping system message {}", textMessage);
            return CompletableFuture.completedFuture(new Parser.Text(""));
        }

        log.info("*** REQUESTED, for pool {}, comment:\n\t {}", facilityId, textMessage);
        var reservationId = UUID.randomUUID().toString().replaceAll("-", "");

        Parser.ReservationDto rDto = parser.parse(facilityId, textMessage);

        // Return immediately with "Processing..." and trigger the action asynchronously
        CompletableFuture.runAsync(() -> {
            if (rDto.command().equals("cancel")) {
                componentClient
                    .forEventSourcedEntity(rDto.reservationId())
                    .method(ReservationEntity::cancelRequest)
                    .invokeAsync();
            } else {
                Reservation r = new Reservation(rDto.emails(), rDto.dateTime());
                ReservationEntity.Init body = new ReservationEntity.Init(r, Set.of(facilityId), "");

                // Register timer then init reservation (same as RezAction)
                timerScheduler.createSingleTimer(
                    RezAction.timerName(reservationId),
                    Duration.ofSeconds(RezAction.TIMEOUT),
                    componentClient
                        .forTimedAction()
                        .method(TimerAction::expire)
                        .deferred(reservationId)
                );

                componentClient
                    .forEventSourcedEntity(reservationId)
                    .method(ReservationEntity::init)
                    .invokeAsync(body)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Error initiating reservation {}: {}", reservationId, error.getMessage());
                        }
                    });
            }
        });

        return CompletableFuture.completedFuture(new Parser.Text("Processing ..."));
    }

    @Get
    public String configureUrl() {
        var queryParams = requestContext().queryParams();
        String install_id = queryParams.getString("install_id").orElse("");
        String post_data_url = queryParams.getString("post_data_url").orElse("");
        String user_id = queryParams.getString("user_id").orElse("");
        String user_name = queryParams.getString("user_name").orElse("");

        log.info("GOT CONFIGURE URL FROM TWIST");
        log.info("\tinstall_id=" + install_id);
        log.info("\tpost_data_url=" + post_data_url);
        log.info("\tuser_id=" + user_id);
        log.info("\tuser_name=" + user_name);

        return "OK";
    }
}
