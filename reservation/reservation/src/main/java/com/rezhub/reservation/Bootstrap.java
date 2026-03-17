package com.rezhub.reservation;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // Register Akka-managed types as Spring singletons so @Component classes can autowire them
        context.getBeanFactory().registerSingleton("componentClient", componentClient);
        context.getBeanFactory().registerSingleton("timerScheduler", timerScheduler);
        context.scan("com.rezhub.reservation");
        context.refresh();
        return context::getBean;
    }
}
