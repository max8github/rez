package com.rez.facility.api;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.spring.KalixClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequestMapping("/outwebhook")
public class WebhookAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(WebhookAction.class);

    private final KalixClient kalixClient;

    public WebhookAction(KalixClient kalixClient) {
        this.kalixClient = kalixClient;
    }

    /**
     * This is the input to rez. It is also called "outgoing webhook" from Twist's standpoint: Twist -to-> Kalix.<br>
     * It is used for initiating the entire processing.
     * When someone types a message in the set-up Twist thread, Twist should trigger the creation of that message and
     * send it out to Kalix, here.
     * @return
     */
    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping()
    public Effect<TwistContent> outwebhook(@RequestBody TwistComment comment) {
        //todo: for now, i take it as if the thread_id was the same as the facility id.
        String facilityId = comment.thread_id();
        String content = comment.content();
        log.info("*** " + comment.creator_name + " REQUESTED, for facility {}, content:\n\t", facilityId, content);
        var path = "/facility/%s/reservation/create".formatted(facilityId);
        Mod.Reservation body = parseComment(comment);
        var deferredCall = kalixClient.post(path, body, TwistContent.class);
        return effects().forward(deferredCall);
    }

    private Mod.Reservation parseComment(TwistComment twistComment) {
        String content = twistComment.content();
        List<String> attendees = new ArrayList<>();
        attendees.add(twistComment.creator_name());//todo: these are names, not emails afaik
        //todo: i should get the emails from the users accounts
        //todo: the assumption for now is something like: 2023-08-02, 8, john.doe@example.com
        try {
            String[] parts = content.split(",");
            LocalDate date = LocalDate.parse(parts[0].trim());
            int hourOfDay = Integer.parseInt(parts[1].trim());
            int timeSlot = (hourOfDay > 23 || hourOfDay < 0) ? 23 : hourOfDay;
            String attendee = parts[2].trim();
            attendees.add(attendee);
            return new Mod.Reservation(attendees, timeSlot, date);
        } catch (Exception e) {
            log.error("COULD NOT PARSE MESSAGE INTO RESERVATION DETAILS. MESSAGE: '" + content + "', exception:\n"
            + e);
            return new Mod.Reservation(attendees, 1, LocalDate.now());
        }
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

    record TwistContent(String content) {}

    /**
     * See <a href="https://developer.twist.com/v3/#comments">here</a> for full object.
     * @param channel_id
     * @param content
     * @param creator
     * @param id
     * @param posted
     */
    record TwistComment(String channel_id, String thread_id, String content, String creator, String creator_name, String id, String posted) {}
}
