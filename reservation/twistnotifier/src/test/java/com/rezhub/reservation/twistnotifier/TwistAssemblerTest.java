package com.rezhub.reservation.twistnotifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rezhub.reservation.spi.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TwistAssemblerTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void objectToJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Parser.TextMessage textMessage = new Parser.TextMessage("chis123", "thid123", "book such and such", "John",
                "John", "u123", "2023-09-20T20:22", new Parser.SystemMessage(123, "https://example.com"), "https://example.com");
        String s = objectMapper.writeValueAsString(textMessage);
        System.out.println("s = " + s);
    }

    @Test
    void jsonToObject() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\"channel_id\":\"chis123\",\"thread_id\":\"thid123\",\"content\":\"book such and such\",\"creator\":\"John\",\"creator_name\":\"John\",\"id\":\"u123\",\"posted\":\"2023-09-20T20:22\",\"system_message\":{\"integration_id\":123,\"url\":\"https://example.com\"},\"url\":\"https://example.com\"}";
        Parser.TextMessage textMessage = objectMapper.readValue(json, Parser.TextMessage.class);
        System.out.println("textMessage = " + textMessage);
    }

    @Test
    void jsonToJsonNode() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\"channel_id\":\"chis123\",\"thread_id\":\"thid123\",\"content\":\"book such and such\",\"creator\":\"John\",\"creator_name\":\"John\",\"id\":\"u123\",\"posted\":\"2023-09-20T20:22\",\"system_message\":{\"integration_id\":123,\"url\":\"https://example.com\"},\"url\":\"https://example.com\"}";
        JsonNode jsonNode = objectMapper.readTree(json);
        String content = jsonNode.get("content").asText();
        System.out.println("content = " + content);
    }

    @Test
    void jsonNodeToObject() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = "{\"channel_id\":\"chis123\",\"thread_id\":\"thid123\",\"content\":\"book such and such\",\"creator\":\"John\",\"creator_name\":\"John\",\"id\":\"u123\",\"posted\":\"2023-09-20T20:22\",\"system_message\":{\"integration_id\":123,\"url\":\"https://example.com\"},\"url\":\"https://example.com\"}";
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        Parser.TextMessage textMessage = objectMapper.treeToValue(jsonNode, Parser.TextMessage.class);
        System.out.println("textMessage = " + textMessage);
    }

}