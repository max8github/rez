package com.rez.facility.spi;

import com.google.protobuf.any.Any;
import com.rez.facility.api.Mod;
import kalix.javasdk.DeferredCall;
import kalix.spring.KalixClient;

public interface Interpreter {
    DeferredCall<Any, Mod.TwistContent> interpret(KalixClient kalixClient, String facilityId, Mod.TwistComment comment);
}
