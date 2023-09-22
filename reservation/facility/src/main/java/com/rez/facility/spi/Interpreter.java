package com.rez.facility.spi;

import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.spring.KalixClient;

public interface Interpreter {
    DeferredCall<Any, TwistContent> interpret(KalixClient kalixClient, String facilityId, TwistComment comment);

    record TwistContent(String content) {}

    /**
     * See <a href="https://developer.twist.com/v3/#comments">here</a> for full object.
     */
    record TwistComment(String channel_id, String thread_id, String content, String creator, String creator_name,
                        String id, String posted, SystemMessage system_message, String url) {}

    record SystemMessage(int integration_id, String url) {}
}
