package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.rezhub.reservation.agent.BookingAgent;
import com.rezhub.reservation.spi.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives Twist outgoing-webhook events and dispatches them to BookingAgent.
 *
 * Setup (Twist side):
 *   - Add an outgoing webhook integration pointing to /twist/webhook
 *   - The thread_id of that Twist thread must match the facility's Akka entity ID
 *   - For the install callback configure /twist/install
 *
 * Each message is forwarded to BookingAgent. The LLM reply is returned
 * synchronously so Twist shows it in the thread immediately ("Got it, checking…"),
 * and DelegatingServiceAction sends the final booking outcome via TwistNotifier.
 */
@HttpEndpoint("/twist")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TwistEndpoint extends AbstractHttpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TwistEndpoint.class);

    private final ComponentClient componentClient;
    private final NotificationSender notificationSender;

    public TwistEndpoint(ComponentClient componentClient, NotificationSender notificationSender) {
        this.componentClient = componentClient;
        this.notificationSender = notificationSender;
    }

    // ---- Twist outgoing-webhook payload (subset of fields we use) ----

    public record TwistMessage(String channel_id, String thread_id, String content,
                               String creator, String creator_name,
                               String id, SystemMessage system_message) {}

    public record SystemMessage(int integration_id, String url) {}

    // ---- Twist install callback ----

    /**
     * Twist calls this GET when the integration is installed.
     * Log the params and return OK so Twist considers the installation valid.
     */
    @Get("/install")
    public String onInstall() {
        var params = requestContext().queryParams();
        log.info("Twist install callback: install_id={}, post_data_url={}, user_id={}, user_name={}",
            params.getString("install_id").orElse(""),
            params.getString("post_data_url").orElse(""),
            params.getString("user_id").orElse(""),
            params.getString("user_name").orElse(""));
        return "OK";
    }

    /**
     * Twist posts each new comment in the configured thread here.
     * Returns 200 immediately; the agent reply and final booking outcome
     * are both sent back via NotificationSender (TwistNotifier).
     */
    @Post("/webhook")
    public void onMessage(TwistMessage msg) {
        if (msg.system_message() != null) {
            log.debug("Dropping Twist system message");
            return;
        }

        String facilityId = msg.thread_id();
        String senderName = msg.creator_name() != null ? msg.creator_name() : msg.creator();
        String recipientId = msg.creator();
        String sessionId = sanitize(facilityId + ":" + recipientId);

        log.info("Twist message from {} (creator {}) in thread {}: {}", senderName, recipientId, facilityId, msg.content());

        componentClient
            .forAgent()
            .inSession(sessionId)
            .method(BookingAgent::chat)
            .invokeAsync(new BookingAgent.BookingRequest(facilityId, senderName, recipientId, null, msg.content()))
            .thenAccept(reply -> notificationSender.send(recipientId, reply))
            .whenComplete((v, error) -> {
                if (error != null) log.error("Agent error for Twist creator {}: {}", recipientId, error.getMessage());
            });
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-.]", "-");
    }
}
