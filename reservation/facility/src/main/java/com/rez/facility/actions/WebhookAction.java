package com.rez.facility.actions;

import com.google.protobuf.any.Any;
import com.mcalder.rez.spi.Assembler;
import com.mcalder.rez.spi.Parser;
import com.rez.facility.dto.Reservation;
import com.rez.facility.entities.FacilityEntity;
import com.rez.facility.entities.ReservationEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.Metadata;
import kalix.javasdk.SideEffect;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/outwebhook")
public class WebhookAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(WebhookAction.class);

    private final ComponentClient kalixClient;
    private final Assembler assembler;
    private final Parser parser;

    public WebhookAction(ComponentClient kalixClient, Assembler assembler, Parser parser) {
        this.kalixClient = kalixClient;
        this.assembler = assembler;
        this.parser = parser;
    }

    /**
     * This is the input to rez. It is also called "outgoing webhook" from Twist's standpoint: Twist -to-> Kalix.<br>
     * It is used for initiating the entire processing.
     * Posting a message in the set-up Twist thread triggers the sending of that message to Kalix, caught here.
     * <br>
     * The action does two things: upon receiving a generic Json object, it first translates that into its appropriate
     * object based on implementor (as there are different text message providers, like Twist).
     * It then interprets what its content says by calling a parser.
     * @return message back to the Text Message provider (Twist), optionally
     */
    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping()
    public Effect<Parser.Text> outwebhook(@RequestBody com.fasterxml.jackson.databind.JsonNode blob) {
        Parser.TextMessage textMessage = assembler.assemble(blob);
        String facilityId = textMessage.thread_id();//thread_id must be the same as the facility id (todo: provisioning).
        if(textMessage.system_message() != null) {//drop it
            log.info("dropping system message {}", textMessage);
            return effects().ignore();
        }
        log.info("*** REQUESTED, for facility {}, comment:\n\t {}", facilityId, textMessage);

        Parser.ReservationDto rDto = parser.parse(facilityId, textMessage);
        DeferredCall<Any, String> deferredCall;
        if(rDto.command().equals("cancel")) {
            deferredCall = kalixClient.forWorkflow(rDto.reservationId()).call(ReservationEntity::cancelRequest).params(rDto.reservationId());
        } else {
            Reservation body = new Reservation(rDto.emails(), rDto.dateTime());
            deferredCall = kalixClient.forEventSourcedEntity(facilityId).call(FacilityEntity::createReservation).params(body);

        }

        return effects().reply(new Parser.Text("Processing ..."), Metadata.EMPTY.add("_kalix-http-code", "202"))
                .addSideEffect(SideEffect.of(deferredCall));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<String> configureUrl(@RequestParam(value = "install_id") String install_id,
                               @RequestParam(value = "post_data_url") String post_data_url,
                               @RequestParam(value = "user_id") String user_id,
                               @RequestParam(value = "user_name") String user_name
                               ) {
        log.info("GOT CONFIGURE URL FROM TWIST");
        log.info("\tinstall_id=" + install_id);
        log.info("\tpost_data_url=" + post_data_url);
        log.info("\tuser_id=" + user_id);
        log.info("\tuser_name=" + user_name);

        return effects().reply("OK");
    }
}
