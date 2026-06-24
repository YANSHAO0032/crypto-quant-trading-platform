package com.quant.backtest;

import com.quant.common.enums.Signal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktestAccountTest {

    @Test
    void buyTradeUpdatesCashPositionFeeAndContinuousEquity() {
        BacktestAccount account = new BacktestAccount(new BigDecimal("100000"), new BigDecimal("0.001"));

        BacktestFill fill = account.execute(Signal.BUY, new BigDecimal("100"), new BigDecimal("2"), 1_700_000_000_000L);
        EquityPoint firstPoint = account.markToMarket(new BigDecimal("100"), 1_700_000_000_000L);
        EquityPoint secondPoint = account.markToMarket(new BigDecimal("110"), 1_700_000_060_000L);

        assertEquals(new BigDecimal("200.00000000"), fill.getNotional());
        assertEquals(new BigDecimal("0.20000000"), fill.getFee());
        assertEquals(new BigDecimal("99799.80000000"), account.getCash());
        assertEquals(new BigDecimal("2.00000000"), account.getPositionQty());
        assertEquals(new BigDecimal("99999.80000000"), firstPoint.getEquity());
        assertEquals(new BigDecimal("100019.80000000"), secondPoint.getEquity());
    }

    @Test
    void shortRoundTripCalculatesNetPnlIncludingFees() {
        BacktestAccount account = new BacktestAccount(new BigDecimal("100000"), new BigDecimal("0.001"));

        account.execute(Signal.SELL, new BigDecimal("100"), BigDecimal.ONE, 1_700_000_000_000L);
        account.markToMarket(new BigDecimal("100"), 1_700_000_000_000L);
        account.execute(Signal.BUY, new BigDecimal("90"), BigDecimal.ONE, 1_700_000_060_000L);
        EquityPoint point = account.markToMarket(new BigDecimal("90"), 1_700_000_060_000L);

        assertEquals(BigDecimal.ZERO.setScale(8), account.getPositionQty());
        assertEquals(new BigDecimal("100009.81000000"), point.getEquity());
        assertEquals(new BigDecimal("9.81000000"), account.getClosedTrades().get(0).getNetPnl());
        assertEquals(new BigDecimal("0.19000000"), account.getTotalFee());
    }
}
