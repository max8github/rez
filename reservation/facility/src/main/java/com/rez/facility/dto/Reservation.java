package com.rez.facility.dto;

import java.time.LocalDateTime;
import java.util.List;

//todo: maybe this is my dto to pass around throughout, in which case, i must also add: fac id, res id, rez id
public record Reservation(List<String> emails, LocalDateTime dateTime) {
}
