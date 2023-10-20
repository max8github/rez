package com.rezhub.reservation.stringparser;

import com.rezhub.reservation.spi.Nlp;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StringNlpTest {

    @Test
    void parseISO() {
        String content = "2023-04-12T14:00, Toni, Meg";
        Nlp parser = new StringNlpParser();
        Nlp.Result result = parser.parse(content);
        assertThat(result.who()).hasSameElementsAs(Set.of("Toni", "Meg"));
        assertEquals(result.when(), "2023-04-12T14:00");
    }

    @Test
    void parse() {
        String content = "2023-04-12, 14, Toni";
        Nlp parser = new StringNlpParser();
        Nlp.Result rez = parser.parse(content);
        assertEquals(rez.who().size(), 1);
        assertEquals(rez.who().iterator().next(), "Toni");
        assertEquals(rez.when(), "2023-04-12T14:00");
    }
}