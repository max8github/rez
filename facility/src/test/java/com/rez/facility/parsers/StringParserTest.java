package com.rez.facility.parsers;

import com.rez.facility.spi.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringParserTest {

    @Test
    void parse() {
        String content = "2023-04-12, 14, Toni";
        Parser parser = new StringParser();
        Parser.Result rez = parser.parse(content);
        assertEquals(rez.who().size(), 1);
        assertEquals(rez.who().iterator().next(), "Toni");
        assertEquals(rez.when(), "2023-04-12T14:00");
    }
}