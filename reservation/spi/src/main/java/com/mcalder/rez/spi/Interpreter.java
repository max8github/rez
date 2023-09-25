package com.mcalder.rez.spi;

import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.spring.KalixClient;

/**
 * The interpreter does two things: upon receiving a generic Json object, it first translates that into its appropriate
 * object based on implementor (as there are different text message providers, like Twist).
 * It then interprets what its content says.
 */
public interface Interpreter {
    /**
     *
     * @param kalixClient
     * @param facilityId
     * @param comment
     * @return
     */
    DeferredCall<Any, Text> interpret(KalixClient kalixClient, String facilityId, TextMessage comment);

    /**
     * It represents the very raw text message content sent by a human through some text message app.
     * @param content
     */
    record Text(String content) {}

    /**
     * See <a href="https://developer.twist.com/v3/#comments">here</a> for full object.
     * This record should represent a generic text message with its metadata.
     * Every text message service will have its own protocol for this kind of data, and so, it will have to be
     * transformed to this type object first.
     *
     * TODO: ideally, this comes first into the interpreter as raw json, not like this.
     * And then, depending on which message service it is in use, the
     * appropriate assembler implementation will transform it into this type of object, TextMessage.
     * So, the Interpreter should make use of an Assembler for transforming to TextMessage, and then the `content` of
     * TextMessage will be parsed by the appropriate Parser to understand what the command is from the user's
     * human-written text.
     */
    record TextMessage(String channel_id, String thread_id, String content, String creator, String creator_name,
                       String id, String posted, SystemMessage system_message, String url) {}

    record SystemMessage(int integration_id, String url) {}
}
