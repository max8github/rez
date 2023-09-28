package com.rez.facility;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see <a href="https://docs.kalix.io/projects/secrets.html">Manage Secrets</a>
 * <br>Need to set from the command line something like:
 * <pre>
 * kalix secret create generic msg-creds --literal INSTALL_TOKEN=t0k3n
 * </pre>
 * <pre>
 * kalix service deploy facility registry.hub.docker.com/max8github/facility:0.4-20230610232357 --secret-env INSTALL_TOKEN=msg-creds/INSTALL_TOKEN
 * </pre>
 */
public class ConfigTest {
    private static final Logger log = LoggerFactory.getLogger(ConfigTest.class);

    @Test
    public void testConfig() {
        Config twistConfig = ConfigFactory.defaultApplication().getConfig("twist");
        String url = twistConfig.getString("url");
        Assertions.assertEquals("https://twist.com/api/v3/integration_incoming/post_data", url);
        String install_id = twistConfig.getString("install_id");
        Assertions.assertEquals("412390", install_id);
        String install_token = System.getenv("INSTALL_TOKEN");
        Assertions.assertEquals("t0k3n", install_token);
        String uri = url + "?install_id=" + install_id + "&install_token=" + install_token;
        log.info("uri = \n\t" + uri);
    }
}
