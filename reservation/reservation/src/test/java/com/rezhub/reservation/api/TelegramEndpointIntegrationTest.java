package com.rezhub.reservation.api;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.rezhub.reservation.agent.BookingAgent;
import com.rezhub.reservation.customer.dto.Address;
import com.rezhub.reservation.customer.facility.FacilityEntity;
import com.rezhub.reservation.customer.facility.dto.Facility;
import com.rezhub.reservation.view.FacilityByBotTokenView;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TelegramEndpoint's identity resolution wiring (spec 001-telegram-identity-resolution).
 *
 * Two real environment constraints shape what these tests can assert, not just how they're written:
 *
 * 1. There is no reachable `identity` service inside TestKitSupport's embedded runtime, and this SDK
 *    version has no way to mock httpClientFor("identity")'s responses in-process (same limitation
 *    IdentityClientTest works around by not extending TestKitSupport at all). So resolveOrCreate always
 *    fails here — every case below exercises the fail-open path, never a successful resolution.
 * 2. This codebase deliberately does not test actual tool-call execution (a real booking happening) via
 *    mocked TestModelProvider tool calls — see BookingAgentIntegrationTest's own doc comment: "Tool call
 *    behaviour ... is exercised by the real LLM in smoke-local.sh; here we just verify the plumbing."
 *    These tests follow that same convention (fixedResponse, no ToolInvocationRequest scripting).
 *
 * Given both constraints, "a Telegram sender's first message mints a new identityUserId, and repeat
 * contact resolves to the same one" (spec.md User Story 1's full claim) is not something this test class
 * can verify — that requires a real, reachable `identity` and a real booking, both out of scope for an
 * automated test here. That claim is verified manually via quickstart.md against a real local stack.
 * What IS verified here: the webhook completes normally (no exception, no broken reply) once
 * IdentityClient is wired into TelegramEndpoint, whether or not the sender is identifiable — i.e. the
 * fail-open guarantee (spec.md User Story 2) holds at the full endpoint level, not just inside
 * IdentityClient's own unit tests.
 */
public class TelegramEndpointIntegrationTest extends TestKitSupport {

    private final TestModelProvider bookingModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
            .withModelProvider(BookingAgent.class, bookingModel);
    }

    @Test
    void webhook_withIdentifiableSender_completesNormally_whenIdentityServiceUnreachable() throws Exception {
        String botToken = provisionFacilityWithBotToken();
        bookingModel.fixedResponse("Sure, let me check availability for you.");

        var update = new TelegramEndpoint.Update(
            new TelegramEndpoint.Message(1L,
                new TelegramEndpoint.From(98765L, "Alex", "alex_tg"),
                new TelegramEndpoint.Chat(555L, "private"),
                "Any courts free tomorrow at 6pm?"));

        var response = httpClient.POST("/telegram/" + botToken + "/webhook")
            .withRequestBody(update)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    void webhook_withNoFromField_doesNotThrow() throws Exception {
        String botToken = provisionFacilityWithBotToken();
        bookingModel.fixedResponse("This message has no identifiable sender.");

        var update = new TelegramEndpoint.Update(
            new TelegramEndpoint.Message(2L, null,
                new TelegramEndpoint.Chat(556L, "channel"),
                "A channel post with no sender"));

        var response = httpClient.POST("/telegram/" + botToken + "/webhook")
            .withRequestBody(update)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }

    // --- helpers ---

    private String provisionFacilityWithBotToken() throws Exception {
        String facilityId = "f_tg-identity-" + shortId();
        String botToken = "bot:tg-identity-" + shortId();

        componentClient.forEventSourcedEntity(facilityId)
            .method(FacilityEntity::create)
            .invoke(new Facility("Identity Test Club", new Address("Test St", "Berlin"),
                "Europe/Berlin", botToken, null));

        eventually(() ->
            componentClient.forView()
                .method(FacilityByBotTokenView::getByBotToken)
                .invoke(botToken),
            Optional::isPresent);

        return botToken;
    }

    private <T> T eventually(CheckedSupplier<T> query, java.util.function.Predicate<T> until) throws Exception {
        T last = null;
        for (int i = 0; i < 80; i++) {
            last = query.get();
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met after 4s. Last value: " + last);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
