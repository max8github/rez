package com.rezhub.reservation;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import com.rezhub.reservation.agent.BookingService;
import com.rezhub.reservation.spi.CalendarSender;
import com.rezhub.reservation.spi.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

@Setup
@Acl(allow = @Acl.Matcher(service = "*"))
public class Bootstrap implements ServiceSetup {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public Bootstrap(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    @Override
    public void onStartup() {
        logger.info("Starting Reservation Application");

        // Diagnose license/env: print key fingerprint and relevant env vars
        String licenseKey = System.getenv("AKKA_LICENSE_KEY");
        if (licenseKey == null || licenseKey.isBlank()) {
            logger.warn("DIAG: AKKA_LICENSE_KEY is not set");
        } else {
            logger.info("DIAG: AKKA_LICENSE_KEY length={} first8={} last8={}",
                licenseKey.length(),
                licenseKey.substring(0, Math.min(8, licenseKey.length())),
                licenseKey.substring(Math.max(0, licenseKey.length() - 8)));
        }
        logger.info("DIAG: DB_HOST={} DB_PORT={} DB_DATABASE={} DB_USER={}",
            System.getenv("DB_HOST"),
            System.getenv("DB_PORT"),
            System.getenv("DB_DATABASE"),
            System.getenv("DB_USER"));

        // Print resolved HOCON values for the r2dbc connection factory
        com.typesafe.config.Config cfg = com.typesafe.config.ConfigFactory.load();
        String cfgPath = "akka.persistence.r2dbc.connection-factory";
        if (cfg.hasPath(cfgPath)) {
            com.typesafe.config.Config cf = cfg.getConfig(cfgPath);
            logger.info("DIAG: r2dbc connection-factory host={} port={} database={} user={}",
                cf.getString("host"),
                cf.getString("port"),
                cf.getString("database"),
                cf.getString("user"));
        } else {
            logger.warn("DIAG: {} not found in config", cfgPath);
        }
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        var bookingService = new BookingService(componentClient, timerScheduler);
        return new DependencyProvider() {
            @Override
            public <T> T getDependency(Class<T> clazz) {
                if (clazz == BookingService.class) {
                    return clazz.cast(bookingService);
                } else if (clazz == CalendarSender.class) {
                    return clazz.cast(ServiceLoader.load(CalendarSender.class).iterator().next());
                } else if (clazz == NotificationSender.class) {
                    return clazz.cast(ServiceLoader.load(NotificationSender.class).iterator().next());
                }
                throw new RuntimeException("No dependency registered for: " + clazz);
            }
        };
    }
}
