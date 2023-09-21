package com.rez.facility.actions;

import com.mcalder.rez.spi.Interpreter;
import kalix.javasdk.Metadata;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

//todo: remove
@RequestMapping("/bottest")
public class StubAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(StubAction.class);

    public StubAction() {
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping()
    public Action.Effect<Interpreter.TwistContent> post(@RequestBody com.fasterxml.jackson.databind.JsonNode command) {
        log.info("command, {}", command);
        return effects().reply(new Interpreter.TwistContent(
                        "Message back"), Metadata.EMPTY.add("_kalix-http-code", "202"));
    }

    @GetMapping()
    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    public Action.Effect<String> get() {
        log.info("get called ****");
        return effects().reply("OK, get");
    }
}