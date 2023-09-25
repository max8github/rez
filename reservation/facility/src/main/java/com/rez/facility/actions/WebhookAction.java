package com.rez.facility.actions;

import com.mcalder.rez.spi.Interpreter;
import kalix.javasdk.Metadata;
import kalix.javasdk.SideEffect;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.spring.KalixClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/outwebhook")
public class WebhookAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(WebhookAction.class);

    private final KalixClient kalixClient;
    private final Interpreter interpreter;

    public WebhookAction(KalixClient kalixClient, Interpreter interpreter) {
        this.kalixClient = kalixClient;
        this.interpreter = interpreter;
    }

    /**
     * This is the input to rez. It is also called "outgoing webhook" from Twist's standpoint: Twist -to-> Kalix.<br>
     * It is used for initiating the entire processing.
     * Posting a message in the set-up Twist thread triggers the sending of that message to Kalix, caught here.
     * @return message back to Twist, optionally
     */
    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping()
    public Effect<Interpreter.Text> outwebhook(@RequestBody Interpreter.TextMessage comment) {
        String facilityId = comment.thread_id();//thread_id must be the same as the facility id (todo: provisioning).
        if(comment.system_message() != null) {//drop it
            log.info("dropping system message {}", comment);
            return effects().ignore();
        }
        log.info("*** REQUESTED, for facility {}, comment:\n\t {}", facilityId, comment);
        var deferredCall = interpreter.interpret(kalixClient, facilityId, comment);
        return effects().reply(new Interpreter.Text("Processing ..."), Metadata.EMPTY.add("_kalix-http-code", "202"))
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
