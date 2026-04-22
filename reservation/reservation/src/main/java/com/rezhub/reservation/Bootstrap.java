package com.rezhub.reservation;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import com.rezhub.reservation.agent.BookingService;
import com.rezhub.reservation.spi.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

@Setup
@Acl(allow = @Acl.Matcher(service = "*"))
public class Bootstrap implements ServiceSetup {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private final ComponentClient componentClient;

    public Bootstrap(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public void onStartup() {
        logger.info("");
        logger.info("*****************************************************");
        logger.info("*****************************************************");
        logger.info("**                                                 **");
        logger.info("**        Starting Reservation Application         **");
        logger.info("**                                                 **");
        logger.info("*****************************************************");
        logger.info("*****************************************************");
        logger.info("");

        // DIAG: confirm license key is present in env (akka.license-key picks it up via HOCON substitution)
        String licenseKey = System.getenv("AKKA_LICENSE_KEY");
        if (licenseKey == null || licenseKey.isBlank()) {
            logger.warn("DIAG: AKKA_LICENSE_KEY is not set");
        } else {
            logger.info("DIAG: AKKA_LICENSE_KEY length={} first8={} last8={}",
                licenseKey.length(),
                licenseKey.substring(0, Math.min(8, licenseKey.length())),
                licenseKey.substring(Math.max(0, licenseKey.length() - 8)));
        }
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        var bookingService = new BookingService(componentClient);
        return new DependencyProvider() {
            @Override
            public <T> T getDependency(Class<T> clazz) {
                if (clazz == BookingService.class) {
                    return clazz.cast(bookingService);
                } else if (clazz == NotificationSender.class) {
                    return clazz.cast(ServiceLoader.load(NotificationSender.class).iterator().next());
                }
                throw new RuntimeException("No dependency registered for: " + clazz);
            }
        };
    }
}
