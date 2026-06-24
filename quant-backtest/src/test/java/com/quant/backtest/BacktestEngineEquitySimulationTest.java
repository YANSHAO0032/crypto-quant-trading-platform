package com.quant.backtest;

import com.quant.backtest.mapper.BacktestReportMapper;
import com.quant.backtest.mapper.BacktestTradeRecordMapper;
import com.quant.common.enums.Signal;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.TickData;
import com.quant.strategy.Strategy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class BacktestEngineEquitySimulationTest {

    @Test
    void runBacktestUsesQuantityFeesAndContinuousEquityCurve() {
        BacktestEngine engine = new BacktestEngine(
                new PerformanceAnalyzer(),
                mock(DataLoader.class),
                mock(BacktestReportMapper.class),
                mock(BacktestTradeRecordMapper.class),
                new DefaultPositionSizer()
        );
        ReflectionTestUtils.setField(engine, "orderQuantity", new BigDecimal("2"));
        ReflectionTestUtils.setField(engine, "feeRate", new BigDecimal("0.001"));

        BacktestReport report = engine.runBacktest(new TwoSignalStrategy(), List.of(
                tick("100", 1_700_000_000_000L),
                tick("110", 1_700_000_060_000L),
                tick("120", 1_700_000_120_000L)
        ), new BigDecimal("100000"));

        assertEquals(new BigDecimal("100019.58000000"), report.getFinalCapital());
        assertEquals(new BigDecimal("0.0002"), report.getTotalReturn());
        assertEquals(1, report.getTotalTrades());
        assertEquals(1, report.getWinCount());
    }

    private TickData tick(String price, long timestamp) {
        return TickData.builder()
                .symbol("BTCUSDT")
                .lastPrice(new BigDecimal(price))
                .timestamp(timestamp)
                .build();
    }

    private static class TwoSignalStrategy implements Strategy {
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
        public Signal onTick(TickData tick) {
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
}
