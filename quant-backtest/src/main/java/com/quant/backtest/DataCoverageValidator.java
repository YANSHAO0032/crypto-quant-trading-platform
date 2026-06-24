package com.quant.backtest;

public class DataCoverageValidator {

    public Coverage validate(String interval, long requestedStartMs, long requestedEndMs,
                             long actualStartMs, long actualEndMs, boolean allowPartialData) {
        long intervalMs = intervalMs(interval);
        long missingBefore = actualStartMs > requestedStartMs
                ? ceilDiv(actualStartMs - requestedStartMs, intervalMs)
                : 0L;
        long nextExpectedOpen = actualEndMs + 1L;
        long missingAfter = nextExpectedOpen < requestedEndMs
                ? ceilDiv(requestedEndMs - nextExpectedOpen, intervalMs)
                : 0L;
        long missingBars = missingBefore + missingAfter;
        boolean complete = missingBars == 0;
        String message = complete
                ? "coverage complete"
                : String.format("kline coverage incomplete: requested=[%d,%d], actual=[%d,%d], missingBars=%d",
                requestedStartMs, requestedEndMs, actualStartMs, actualEndMs, missingBars);

        Coverage coverage = new Coverage(complete, missingBars, message);
        if (!complete && !allowPartialData) {
            throw new IllegalArgumentException(message);
        }
        return coverage;
    }

    public long intervalMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            default -> throw new IllegalArgumentException("unsupported interval: " + interval);
        };
    }

    private long ceilDiv(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
    }

    public record Coverage(boolean coverageComplete, long missingBars, String message) {
    }
}
