package com.rezhub.reservation.dto;

import java.time.LocalDateTime;
import java.util.List;

//todo: maybe this is my dto to pass around throughout, in which case, i must also add: fac id, res id, rez id
public record Reservation(List<String> emails, LocalDateTime dateTime, int durationMinutes) {
  public static final String DELIMITER = "_";
  public static final String FACILITY = "f"+DELIMITER;
  public static final String RESOURCE = "r"+DELIMITER;

  /** Callers that don't yet know a real duration get this default, matching the historical 1-hour assumption. */
  public static final int DEFAULT_DURATION_MINUTES = 60;

  public Reservation(List<String> emails, LocalDateTime dateTime) {
    this(emails, dateTime, DEFAULT_DURATION_MINUTES);
  }
}
