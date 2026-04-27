package com.rezhub.reservation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.agent.BookingAgent;
import com.rezhub.reservation.orchestration.OriginRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HTTP endpoint called by the Matrix bot (bot.py) running on lurch/synapse.
 *
 * The bot POSTs each incoming room message here. This endpoint invokes the BookingAgent
 * in a session scoped to the room + sender, then returns the agent's reply as plain text.
 * The bot posts the reply back to the Matrix room.
 *
 * Session continuity: Akka automatically stores conversation history keyed by sessionId,
 * so multi-turn conversations ("only 15:00 is free — shall I book it?") work out of the box.
 *
 * facilityId convention: the Matrix room_id IS the facilityId for the associated club.
 * The bot must be configured with the correct facilityId for each room it monitors.
 */
@HttpEndpoint("/matrix")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class MatrixEndpoint {

    private static final Logger log = LoggerFactory.getLogger(MatrixEndpoint.class);

    private final ComponentClient componentClient;

    public MatrixEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    /**
     * Incoming message from the Matrix bot.
     *
     * @param msg  contains facility_id, sender Matrix ID, sender display name, and message text
     */
    @Post("/message")
    public Reply onMessage(MatrixMessage msg) {
        log.info("Matrix message from {} in facility {}: {}", msg.sender(), msg.facility_id(), msg.message());

        // Session per user per facility — keeps conversations isolated between players
        String sessionId = sanitize(msg.facility_id() + ":" + msg.sender());

        OriginRequestContext origin = new OriginRequestContext(
            "matrix",
            msg.sender(),
            msg.sender_name(),
            msg.facility_id(),
            sessionId,
            Map.of("facilityId", msg.facility_id())
        );

        String reply = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(BookingAgent::chat)
            .invoke(new BookingAgent.AgentRequest(origin, msg.message()));

        return new Reply(reply);
    }

    /**
     * Message sent by the Matrix bot to this endpoint.
     *
     * @param facility_id  Akka entity ID of the facility (configured in bot.py per room)
     * @param sender       Matrix user ID, e.g. "@max3:fritz.box"
     * @param sender_name  Display name, e.g. "Max"
     * @param message      The player's raw text message
     */
    public record MatrixMessage(String facility_id, String sender, String sender_name, String message) {}

    /** The reply to be posted back to the Matrix room. */
    public record Reply(String reply) {}

    /** Strip characters that are invalid in Akka entity/session IDs. */
    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-.]", "-");
    }
}
