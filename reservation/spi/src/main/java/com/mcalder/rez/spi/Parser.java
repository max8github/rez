package com.mcalder.rez.spi;

import java.util.Set;

public interface Parser {
    record Result(Set<String> who, String when, String what, String action) {} //todo: needed?

    Result parse(String msgText);
}
