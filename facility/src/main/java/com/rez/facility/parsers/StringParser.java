package com.rez.facility.parsers;

import com.rez.facility.spi.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
public class StringParser implements Parser {
    private static final Logger log = LoggerFactory.getLogger(StringParser.class);

    @Override
    public Result parse(String content) {
        //todo: the assumption for now is something like: 2023-08-02, 8, john.doe@example.com
        try {
            String[] parts = content.split(",");
            LocalDate date = LocalDate.parse(parts[0].trim());
            int hourOfDay = Integer.parseInt(parts[1].trim());
            hourOfDay = (hourOfDay > 23 || hourOfDay < 0) ? 23 : hourOfDay;
            String dateTime = date.atTime(java.time.LocalTime.of(hourOfDay, 0)).toString();

            String attendee = parts[2].trim();
            Set<String> attendees = new HashSet<>();
            attendees.add(attendee);
            return new Result(attendees, dateTime, "btoken", "book");//todo: btoken is hardcoded, and so is action
        } catch (Exception e) {
            log.error("COULD NOT PARSE MESSAGE INTO RESERVATION DETAILS. MESSAGE: '" + content + "', exception:\n" + e);
//            return new Result(Set.of(""), LocalDateTime.now().toString(), "btoken", "book");
            throw new RuntimeException(e);
        }
    }
}
