package com.rezhub.reservation.stringparser;

import com.rezhub.reservation.spi.Parser;
import com.rezhub.reservation.spi.Nlp;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 */
@RequiredArgsConstructor
@Component
public class TextMessageParser implements Parser {
    private static final Logger log = LoggerFactory.getLogger(TextMessageParser.class);
    private final Nlp nlp;

    @Override
    public ReservationDto parse(String facilityId, TextMessage textMessage) {
        String content = textMessage.content().trim().toLowerCase();
        ReservationDto dto;
        if(content.startsWith("cancel")) {
            String replaced = content.replace("cancel", "").trim();
            var tok = new StringTokenizer(replaced);
            String reservationId = tok.nextToken();
            dto = new ReservationDto(facilityId, reservationId, Collections.emptyList(), LocalDateTime.now(),
                    "cancel");
        } else {
            try {
                dto = commentToReservation(facilityId, textMessage);
            } catch (Exception e) {
                log.warn("Incoming message could not be parsed. Message:\n{}", content);
                throw new RuntimeException(
                        "Message could not be understood: please try again. Format: '2023-01-04, 4, Names'" +
                                "for reservation requests and 'Cancel <reservation id>' for cancellation requests", e);
            }
        }
        return dto;
    }



    private ReservationDto commentToReservation(String facilityId, TextMessage textMessage) {
        List<String> attendees = new ArrayList<>();
        attendees.add(textMessage.creator_name());//todo: these are names, not emails afaik
        //todo: i should get the emails from the users accounts
        Nlp.Result parseResult = nlp.parse(textMessage.content());
        String when = parseResult.when();
        LocalDateTime localDateTime = LocalDateTime.parse(when);

        List<String> attendeesAndCreator = new ArrayList<>(parseResult.who().stream().toList());
        attendeesAndCreator.addAll(attendees);
        return new ReservationDto(facilityId, "#####", attendeesAndCreator, localDateTime,
                "create");
    }

}
