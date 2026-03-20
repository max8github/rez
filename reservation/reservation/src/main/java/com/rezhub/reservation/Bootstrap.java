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
