package com.quant.backtest;

import com.quant.common.model.BacktestReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceAnalyzerPrecisionTest {

    @Test
    void sharpeDoesNotRoundTinyReturnVarianceToZero() {
        BacktestRunResult result = new BacktestRunResult(
                new BigDecimal("5000"),
                List.of(),
                List.of(),
                List.of(
                        point("2026-01-01T00:00:00Z", "5000.00000000"),
                        point("2026-01-02T00:00:00Z", "5000.00500000"),
                        point("2026-01-03T00:00:00Z", "5000.01000000"),
                        point("2026-01-04T00:00:00Z", "5000.00600000")
                )
        );

        BacktestReport report = new PerformanceAnalyzer().analyze(result);

        assertTrue(report.getSharpeRatio().compareTo(BigDecimal.ZERO) != 0);
    }

    @Test
    void dailyStatsUseShanghaiTimezoneByDefault() {
        BacktestRunResult result = new BacktestRunResult(
                new BigDecimal("5000"),
                List.of(),
                List.of(),
                List.of(point(1_767_196_800_000L, "5000.00000000"))
        );

        BacktestReport report = new PerformanceAnalyzer().analyze(result);

        assertEquals(LocalDate.of(2026, 1, 1), report.getDailyStats().get(0).getDate());
    }

    private EquityPoint point(String instant, String equity) {
        return point(java.time.Instant.parse(instant).toEpochMilli(), equity);
    }

    private EquityPoint point(long timestampMs, String equity) {
        return EquityPoint.builder()
                .timestampMs(timestampMs)
                .equity(new BigDecimal(equity))
                .cash(BigDecimal.ZERO)
                .positionQty(BigDecimal.ZERO)
                .markPrice(BigDecimal.ZERO)
                .realizedPnl(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .totalFee(BigDecimal.ZERO)
                .build();
    }
}
