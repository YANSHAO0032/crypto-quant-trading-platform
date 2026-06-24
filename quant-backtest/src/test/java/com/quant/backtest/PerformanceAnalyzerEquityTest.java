package com.quant.backtest;

import com.quant.common.model.BacktestDailyStats;
import com.quant.common.model.BacktestReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceAnalyzerEquityTest {

    @Test
    void calculatesReturnDrawdownAndDailyReturnsFromContinuousEquityCurve() {
        BacktestRunResult result = new BacktestRunResult(
                new BigDecimal("100000"),
                List.of(),
                List.of(),
                List.of(
                        point("2026-01-01T00:00:00Z", "100000"),
                        point("2026-01-01T12:00:00Z", "110000"),
                        point("2026-01-02T00:00:00Z", "99000"),
                        point("2026-01-02T12:00:00Z", "105000")
                )
        );

        BacktestReport report = new PerformanceAnalyzer().analyze(result);
        List<BacktestDailyStats> dailyStats = report.getDailyStats();

        assertEquals(new BigDecimal("105000.00000000"), report.getFinalCapital());
        assertEquals(new BigDecimal("0.0500"), report.getTotalReturn());
        assertEquals(new BigDecimal("0.10000000"), report.getMaxDrawdown());
        assertEquals(2, dailyStats.size());
        assertEquals(new BigDecimal("0.1000"), dailyStats.get(0).getReturnRate());
        assertEquals(new BigDecimal("-0.0455"), dailyStats.get(1).getReturnRate());
    }

    private EquityPoint point(String instant, String equity) {
        return EquityPoint.builder()
                .timestampMs(java.time.Instant.parse(instant).toEpochMilli())
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
