package com.rezhub.reservation.agent;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for BookingAgent using TestModelProvider.
 *
 * These tests prove the agent wiring: request in → model mocked → response out.
 * Tool call behaviour (checkAvailability, bookCourt, cancelReservation) is exercised
 * by the real LLM in smoke-local.sh; here we just verify the plumbing.
 */
public class BookingAgentIntegrationTest extends TestKitSupport {

    private final TestModelProvider bookingModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
            .withModelProvider(BookingAgent.class, bookingModel);
    }

    // --- Test 1: happy path — model returns a booking confirmation ---

    @Test
    void chat_modelReturnsConfirmation_replyIsRelayed() {
        bookingModel.fixedResponse("You're all set! Court 1 booked for Max on 2026-03-22 at 11:00. See you there!");

        String sessionId = UUID.randomUUID().toString();
        var request = new BookingAgent.BookingRequest(
            "facility-smoke-1", "Max", "chat-100", "Europe/Berlin",
            "Book a court for tomorrow at 11am");

        String reply = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(BookingAgent::chat)
            .invoke(request);

        assertThat(reply).isNotBlank();
        assertThat(reply).contains("Court 1");
    }

    // --- Test 2: no-availability scenario — model relays "no slots" message ---

    @Test
    void chat_withProvisionedFacility_modelReturnsNoAvailability() {
        String facilityId = "f_agent-it-" + shortId();

        // Provision facility so the entity exists (agent may call FacilityEntity via tools)
        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(new Facility("Agent Test Club", new Address("Test St", "Berlin"),
                Collections.emptySet(), "Europe/Berlin", null, null));

        // Model says no slots free — relayed directly without tool calls
        bookingModel.fixedResponse("Sorry, no courts available at 11:00. The next free slot is 13:00.");

        String sessionId = UUID.randomUUID().toString();
        var request = new BookingAgent.BookingRequest(
            facilityId, "Max", "chat-200", "Europe/Berlin",
            "Any free courts tomorrow at 11?");

        String reply = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(BookingAgent::chat)
            .invoke(request);

        assertThat(reply).isNotBlank();
        assertThat(reply).contains("13:00");
    }

    // --- helpers ---

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
