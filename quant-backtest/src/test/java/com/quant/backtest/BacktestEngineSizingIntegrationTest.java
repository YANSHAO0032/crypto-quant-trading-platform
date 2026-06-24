package com.quant.backtest;

import com.quant.backtest.mapper.BacktestReportMapper;
import com.quant.backtest.mapper.BacktestTradeRecordMapper;
import com.quant.common.enums.Signal;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.Kline;
import com.quant.strategy.Strategy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestEngineSizingIntegrationTest {

    @Test
    void klineBacktestSizesEntryFromEquityPercentAndClosesSameQuantity() {
        DataLoader loader = mock(DataLoader.class);
        BacktestEngine engine = new BacktestEngine(
                new PerformanceAnalyzer(),
                loader,
                mock(BacktestReportMapper.class),
                mock(BacktestTradeRecordMapper.class),
                new DefaultPositionSizer()
        );
        ReflectionTestUtils.setField(engine, "feeRate", new BigDecimal("0.001"));

        when(loader.loadKlines("BTCUSDT", "1m", 1_700_000_000_000L, 1_700_000_180_000L))
                .thenReturn(List.of(
                        kline("100000", 1_700_000_000_000L),
                        kline("101000", 1_700_000_060_000L),
                        kline("101000", 1_700_000_120_000L)
                ));

        BacktestReport report = engine.runKlineBacktest(new BuyThenSellStrategy(), "BTCUSDT", "1m",
                1_700_000_000_000L, 1_700_000_180_000L, new BigDecimal("5000"),
                BacktestConfig.builder()
                        .sizingMode(PositionSizingMode.EQUITY_PERCENT)
                        .equityPercent(new BigDecimal("0.2"))
                        .allowPartialData(true)
                        .timezone("Asia/Shanghai")
                        .build());

        assertEquals(new BigDecimal("5007.99000000"), report.getFinalCapital());
        assertEquals(new BigDecimal("0.0016"), report.getTotalReturn());
        assertEquals(1, report.getTotalTrades());
        assertEquals("EQUITY_PERCENT", report.getSizingMode());
        assertEquals(new BigDecimal("0.2"), report.getEquityPercent());
        assertEquals(new BigDecimal("0.001"), report.getFeeRate());
        assertEquals(new BigDecimal("2.01000000"), report.getTotalFee());
        assertEquals(0, report.getRejectedOrders());
    }

    @Test
    void klineBacktestRejectsIncompleteCoverageByDefault() {
        DataLoader loader = mock(DataLoader.class);
        BacktestEngine engine = engine(loader);
        when(loader.loadKlines("BTCUSDT", "1m", 1_700_000_000_000L, 1_700_000_180_000L))
                .thenReturn(List.of(
                        kline("100000", 1_700_000_000_000L),
                        kline("100000", 1_700_000_060_000L)
                ));

        assertThrows(IllegalArgumentException.class, () -> engine.runKlineBacktest(new HoldStrategy(),
                "BTCUSDT", "1m", 1_700_000_000_000L, 1_700_000_180_000L, new BigDecimal("5000"),
                BacktestConfig.builder()
                        .sizingMode(PositionSizingMode.FIXED_QTY)
                        .orderQuantity(new BigDecimal("0.001"))
                        .allowPartialData(false)
                        .build()));
    }

    @Test
    void klineBacktestReportsIncompleteCoverageWhenAllowed() {
        DataLoader loader = mock(DataLoader.class);
        BacktestEngine engine = engine(loader);
        when(loader.loadKlines("BTCUSDT", "1m", 1_700_000_000_000L, 1_700_000_180_000L))
                .thenReturn(List.of(
                        kline("100000", 1_700_000_000_000L),
                        kline("100000", 1_700_000_060_000L)
                ));

        BacktestReport report = engine.runKlineBacktest(new HoldStrategy(),
                "BTCUSDT", "1m", 1_700_000_000_000L, 1_700_000_180_000L, new BigDecimal("5000"),
                BacktestConfig.builder()
                        .sizingMode(PositionSizingMode.FIXED_QTY)
                        .orderQuantity(new BigDecimal("0.001"))
                        .allowPartialData(true)
                        .build());

        assertFalse(report.getCoverageComplete());
        assertEquals(1L, report.getMissingBars());
    }

    private BacktestEngine engine(DataLoader loader) {
        BacktestEngine engine = new BacktestEngine(
                new PerformanceAnalyzer(),
                loader,
                mock(BacktestReportMapper.class),
                mock(BacktestTradeRecordMapper.class),
                new DefaultPositionSizer()
        );
        ReflectionTestUtils.setField(engine, "feeRate", new BigDecimal("0.001"));
        return engine;
    }

    private Kline kline(String closePrice, long openTime) {
        BigDecimal price = new BigDecimal(closePrice);
        return Kline.builder()
                .openTime(openTime)
                .closeTime(openTime + 59_999L)
                .openPrice(price)
                .highPrice(price)
                .lowPrice(price)
                .closePrice(price)
                .volume(BigDecimal.ONE)
                .quoteVolume(price)
                .tradeCount(1)
                .build();
    }

    private static class BuyThenSellStrategy implements Strategy {
        private int ticks;

        @Override
        public String getStrategyId() {
            return "TEST";
        }

        @Override
        public String getStrategyName() {
            return "Test Strategy";
        }

        @Override
        public void init() {
            ticks = 0;
        }

        @Override
        public Signal onTick(com.quant.common.model.TickData tick) {
            ticks++;
            if (ticks == 1) return Signal.BUY;
            if (ticks == 2) return Signal.SELL;
            return Signal.HOLD;
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }

    private static class HoldStrategy implements Strategy {

        @Override
        public String getStrategyId() {
            return "HOLD";
        }

        @Override
        public String getStrategyName() {
            return "Hold Strategy";
        }

        @Override
        public void init() {
        }

        @Override
        public Signal onTick(com.quant.common.model.TickData tick) {
            return Signal.HOLD;
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
