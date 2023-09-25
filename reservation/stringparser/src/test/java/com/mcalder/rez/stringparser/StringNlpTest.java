package com.mcalder.rez.stringparser;

import com.mcalder.rez.spi.Nlp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringNlpTest {

    @Test
    void parse() {
        String content = "2023-04-12, 14, Toni";
        Nlp nlp = new StringNlpParser();
        Nlp.Result rez = nlp.parse(content);
        assertEquals(rez.who().size(), 1);
        assertEquals(rez.who().iterator().next(), "Toni");
        assertEquals(rez.when(), "2023-04-12T14:00");
    }
}