package com.rez.facility.api;

import com.google.api.client.json.GenericJson;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.spring.WebClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@RequestMapping("/outwebhook")
public class WebhookAction extends Action {
    private static final Logger log = LoggerFactory.getLogger(WebhookAction.class);

    final private WebClient webClient;

    public WebhookAction(WebClientProvider webClientProvider) {
        this.webClient = webClientProvider.webClientFor("twistwebhook");
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @PostMapping()
    public Effect<TwistContent> outwebhook(@RequestBody GenericJson payload) {
        log.info("***************** SOMEONE POSTED TO TWIST!!!!!!!!!!!!!!!!!!!\n\t"+ payload);
        return effects().reply(new TwistContent("42 is the answer to everything"));
    }

    @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
    @GetMapping()
    public Effect<String> configureUrl(@RequestParam(value = "install_id") String install_id,
                               @RequestParam(value = "post_data_url") String post_data_url,
                               @RequestParam(value = "user_id") String user_id,
                               @RequestParam(value = "user_name") String user_name
                               ) {
        log.info("GOT CONFIGURE URL FROM TWIST");
        log.info("\tinstall_id=" + install_id);
        log.info("\tpost_data_url=" + post_data_url);
        log.info("\tuser_id=" + user_id);
        log.info("\tuser_name=" + user_name);

        return effects().reply("OK");
    }

    record TwistContent(String content) {}
}
