package com.rez.facility.api;

import kalix.javasdk.annotations.TypeName;

public sealed interface BookCommands {
    @TypeName("book-command")
    record BookCommand(String reservationId, String facilityId, Mod.Reservation reservation) implements BookCommands {}
}