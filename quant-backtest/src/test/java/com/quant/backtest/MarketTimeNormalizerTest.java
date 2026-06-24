package com.quant.backtest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketTimeNormalizerTest {

    @Test
    void normalizesMillisecondsMicrosecondsAndNanosecondsToEpochMillis() {
        long millis = 1_577_836_800_000L;
        long micros = 1_780_236_300_000_000L;
        long nanos = 1_780_236_300_000_000_000L;

        assertEquals(1_577_836_800_000L, MarketTimeNormalizer.toEpochMillis(millis));
        assertEquals(1_780_236_300_000L, MarketTimeNormalizer.toEpochMillis(micros));
        assertEquals(1_780_236_300_000L, MarketTimeNormalizer.toEpochMillis(nanos));
    }
}
