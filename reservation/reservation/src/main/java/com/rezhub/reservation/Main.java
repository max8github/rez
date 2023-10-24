package com.rezhub.reservation;

import kalix.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
  "com.rezhub.reservation", "com.rezhub.reservation.stringparser", "com.rezhub.reservation.calendarstub",
  "com.rezhub.reservation.notifierstub"})
@Acl(allow = @Acl.Matcher(service = "*")) // Documentation at https://docs.kalix.io/services/using-acls.html
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    logger.info("Starting Kalix Application");
    SpringApplication.run(Main.class, args);
  }
}