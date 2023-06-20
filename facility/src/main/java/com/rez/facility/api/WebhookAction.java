package com.rez.facility.api;

import com.rez.facility.spi.Parser;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.spring.KalixClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RequestMapping("/outwebhook")
public class WebhookAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(WebhookAction.class);

    private final KalixClient kalixClient;
    private final Parser parser;

    public WebhookAction(KalixClient kalixClient, Parser parser) {
        this.kalixClient = kalixClient;
        this.parser = parser;
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
        Mod.Reservation body = commentToReservation(comment);
        var deferredCall = kalixClient.post(path, body, TwistContent.class);
        return effects().forward(deferredCall);
    }

    private Mod.Reservation commentToReservation(TwistComment twistComment) {
        List<String> attendees = new ArrayList<>();
        attendees.add(twistComment.creator_name());//todo: these are names, not emails afaik
        //todo: i should get the emails from the users accounts
        //todo: the assumption for now is something like: 2023-08-02, 8, john.doe@example.com
        return parseContent(attendees, twistComment.content());
    }

    Mod.Reservation parseContent(List<String> attendees, String content) {
        Parser.Result parseResult = parser.parse(content);
        String when = parseResult.when();
        LocalDateTime localDateTime = LocalDateTime.parse(when);
        LocalDate localDate = localDateTime.toLocalDate();
        LocalTime localTime = localDateTime.toLocalTime();
        int timeSlot = localTime.getHour();

        List<String> attendeesAndCreator = new ArrayList<>(parseResult.who().stream().toList());
        attendeesAndCreator.addAll(attendees);
        return new Mod.Reservation(attendeesAndCreator, timeSlot, localDate);
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
