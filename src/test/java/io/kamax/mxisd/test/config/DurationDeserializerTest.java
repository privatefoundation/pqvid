package io.kamax.mxisd.test.config;

import static org.junit.Assert.assertEquals;

import io.kamax.mxisd.config.DurationDeserializer;
import org.junit.Test;

public class DurationDeserializerTest {

    @Test
    public void durationLoadTest() {
        DurationDeserializer deserializer = new DurationDeserializer();

        assertEquals(4, deserializer.deserialize("4s"));
        assertEquals((60 * 60) + 4, deserializer.deserialize("1h 4s"));
        assertEquals((2 * 60) + 4, deserializer.deserialize("2m 4s"));
        assertEquals((2 * 60 * 60) + (7 * 60) + 4, deserializer.deserialize("2h 7m 4s"));
        assertEquals((60 * 60 * 24) + (2 * 60 * 60) + (7 * 60) + 4, deserializer.deserialize("1d 2h 7m 4s"));
    }
}
