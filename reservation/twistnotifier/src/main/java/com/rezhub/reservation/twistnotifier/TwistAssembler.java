package com.rezhub.reservation.twistnotifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rezhub.reservation.spi.Assembler;
import com.rezhub.reservation.spi.Parser;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class TwistAssembler implements Assembler {
    private final ObjectMapper objectMapper;

    /**
     * Assembling here is trivial, because TextMessage was modeled after the very Twist API.
     * In general, however, a transformation is needed to form a TextMessage (see tests).
     * @param jsonNode the json blob coming from outside
     * @return our internal API object modeling a text message with its metadata
     */
    @Override
    public Parser.TextMessage assemble(JsonNode jsonNode) {
        try {
            return objectMapper.treeToValue(jsonNode, Parser.TextMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
