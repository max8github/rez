package com.rezhub.reservation.spi;

import java.util.Set;

public interface Nlp {
    record Result(Set<String> who, String when, String what, String action) {} //todo: needed?

    Result parse(String msgText);
}
