package com.rezhub.reservation;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Setup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Setup
@Acl(allow = @Acl.Matcher(service = "*"))
public class Bootstrap implements ServiceSetup {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    @Override
    public void onStartup() {
        logger.info("Starting Reservation Application");
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.scan("com.rezhub.reservation");
        context.refresh();
        return context::getBean;
    }
}
