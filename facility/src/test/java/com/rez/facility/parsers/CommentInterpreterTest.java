package com.rez.facility.parsers;

import com.google.protobuf.any.Any;
import com.rez.facility.api.Mod;
import kalix.javasdk.DeferredCall;
import org.junit.jupiter.api.Test;

import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.*;

class CommentInterpreterTest {

    @Test
    void interpret() {
        String content = "Cancel 1234a325bf34 ok?\n".trim().toLowerCase();
        DeferredCall<Any, Mod.TwistContent> deferredCall;
        assertTrue(content.startsWith("cancel"));
        String replaced = content.replace("cancel", "").trim();
        var tok = new StringTokenizer(replaced);
        String reservationId = tok.nextToken();
        assertEquals("1234a325bf34", reservationId);
    }
}