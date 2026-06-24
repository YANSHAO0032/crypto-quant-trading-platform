package com.quant.backtest;

public final class MarketTimeNormalizer {

    private static final long MICROSECOND_THRESHOLD = 1_000_000_000_000_000L;
    private static final long NANOSECOND_THRESHOLD = 1_000_000_000_000_000_000L;

    private MarketTimeNormalizer() {
    }

    public static long toEpochMillis(long rawTimestamp) {
        if (rawTimestamp >= NANOSECOND_THRESHOLD) {
            return rawTimestamp / 1_000_000L;
        }
        if (rawTimestamp >= MICROSECOND_THRESHOLD) {
            return rawTimestamp / 1_000L;
        }
        return rawTimestamp;
    }
}
