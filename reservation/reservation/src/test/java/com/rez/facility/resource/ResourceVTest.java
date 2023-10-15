package com.rez.facility.resource;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Problem: 'Map' type not supported as type by protobuf module
 * It happens when I have a `Map` as a field in `ResourceState` and I use it in a View (ResourceView).
 * For that reason, in `ResourceState` I now have `Set<ResourceState.Entry>` so to mimic a map.
 */
class ResourceVTest {

    @Getter
    static
    class ResourceStat {
        Map<String,String> map;
    }

    @Test
    public void timeWindow() {
        ProtobufMapper mapper = new ProtobufMapper();
        UnsupportedOperationException ex = Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            ProtobufSchema schemaWrapper = mapper.generateSchemaFor(ResourceStat.class);
            System.out.println(schemaWrapper.getSource());
        }, "UnsupportedOperationException was expected");

        Assertions.assertEquals("'Map' type not supported as type by protobuf module", ex.getMessage());


    }
}