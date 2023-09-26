package com.mcalder.rez.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface Assembler {
    Parser.TextMessage assemble(JsonNode blob);
}
