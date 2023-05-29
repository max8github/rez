package com.rez.facility.api;

import kalix.javasdk.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Random;

public class StubAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(StubAction.class);
    private static final Random rand = new Random();

    public StubAction() {
    }

    @PostMapping("/calendar/save")
    public Action.Effect<String> addAndReturn(@RequestBody ResourceEntity.InquireBooking command) {
        var ok = rand.nextBoolean();
        log.info("InquireBooking, " + ok + ": {}", command);
        if (ok)
            return effects().reply("OK, " + command.reservation());
        else
            return effects().error("Error, " + command.reservation());
    }
}