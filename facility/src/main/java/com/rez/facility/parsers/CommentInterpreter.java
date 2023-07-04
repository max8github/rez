package com.rez.facility.parsers;

import com.google.protobuf.any.Any;
import com.rez.facility.api.Mod;
import com.rez.facility.spi.Interpreter;
import com.rez.facility.spi.Parser;
import kalix.javasdk.DeferredCall;
import kalix.spring.KalixClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Component
public class CommentInterpreter implements Interpreter {
    private static final Logger log = LoggerFactory.getLogger(CommentInterpreter.class);
    private final Parser parser;

    public CommentInterpreter(Parser parser) {
        this.parser = parser;
    }

    @Override
    public DeferredCall<Any, Mod.TwistContent> interpret(KalixClient kalixClient, String facilityId, Mod.TwistComment comment) {
        String content = comment.content();
        String path;
        DeferredCall<Any, Mod.TwistContent> deferredCall;
        if(content.trim().toLowerCase().startsWith("cancel")) {
            String contentLow = content.toLowerCase();
            String replaced = contentLow.replace("cancel", "");
            var tok = new StringTokenizer(replaced);
            String reservationId = tok.nextToken();
            path = "/reservation/%s/cancelRequest".formatted(reservationId);
            deferredCall = kalixClient.delete(path, Mod.TwistContent.class);
        } else {
            path = "/facility/%s/reservation/create".formatted(facilityId);
            try {
                Mod.Reservation body = commentToReservation(comment);
                deferredCall = kalixClient.post(path, body, Mod.TwistContent.class);
            } catch (Exception e) {
                log.warn("Incoming message could not be parsed. Message:\n{}", content);
                throw new RuntimeException(
                        "Message could not be understood: please try again. Format: '2023-01-04, 4, Names'" +
                                "for reservation requests and 'Cancel <reservation id>' for cancellation requests", e);
            }
        }
        return deferredCall;
    }



    private Mod.Reservation commentToReservation(Mod.TwistComment twistComment) {
        List<String> attendees = new ArrayList<>();
        attendees.add(twistComment.creator_name());//todo: these are names, not emails afaik
        //todo: i should get the emails from the users accounts
        Parser.Result parseResult = parser.parse(twistComment.content());
        String when = parseResult.when();
        LocalDateTime localDateTime = LocalDateTime.parse(when);
        LocalDate localDate = localDateTime.toLocalDate();
        LocalTime localTime = localDateTime.toLocalTime();
        int timeSlot = localTime.getHour();

        List<String> attendeesAndCreator = new ArrayList<>(parseResult.who().stream().toList());
        attendeesAndCreator.addAll(attendees);
        return new Mod.Reservation(attendeesAndCreator, timeSlot, localDate);
    }

}
