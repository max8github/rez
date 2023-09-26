package com.mcalder.rez.stringparser;

import com.mcalder.rez.spi.Nlp;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor
@Component
public class StringNlpParser implements Nlp {
    private static final Logger log = LoggerFactory.getLogger(StringNlpParser.class);

    @Override
    public Result parse(String content) {
        //todo: the assumption for now is something like: 2023-08-02, 8, john.doe@example.com or:
        //2023-08-02T08:00, john.doe@example.com
        try {
            String dateTime;
            Set<String> attendees;

            String regex = "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}),(.*)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);
            boolean matches = matcher.matches();

            if(matches) {
                String date = matcher.group(1);
                dateTime = LocalDateTime.parse(date).toString();
                String atts = matcher.group(2);
                String[] parts = atts.split(",");
                attendees = Arrays.stream(parts).map(String::trim).collect(Collectors.toSet());
            } else {
                String[] parts = content.split(",");
                LocalDate date = LocalDate.parse(parts[0].trim());
                int hourOfDay = Integer.parseInt(parts[1].trim());
                hourOfDay = (hourOfDay > 23 || hourOfDay < 0) ? 23 : hourOfDay;
                dateTime = date.atTime(java.time.LocalTime.of(hourOfDay, 0)).toString();
                String attendee = parts[2].trim();
                attendees = new HashSet<>();
                attendees.add(attendee);
            }
            return new Result(attendees, dateTime, "btoken", "book");//todo: btoken is hardcoded, and so is action
        } catch (Exception e) {
            log.error("COULD NOT PARSE MESSAGE INTO RESERVATION DETAILS. MESSAGE: '" + content + "', exception:\n" + e);
//            return new Result(Set.of(""), LocalDateTime.now().toString(), "btoken", "book");
            throw new RuntimeException(e);
        }
    }
}
