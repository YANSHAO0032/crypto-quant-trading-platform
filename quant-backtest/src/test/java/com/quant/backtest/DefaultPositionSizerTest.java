package com.quant.backtest;

import com.quant.common.enums.Signal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.quant.backtest.PositionSizingMode.EQUITY_PERCENT;
import static com.quant.backtest.PositionSizingMode.FIXED_NOTIONAL;
import static com.quant.backtest.PositionSizingMode.FIXED_QTY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultPositionSizerTest {

    @Test
    void fixedQtyUsesConfiguredQuantity() {
        assertEquals(new BigDecimal("0.00100000"), size(FIXED_QTY, "5000", "100000", "0.001", null, null));
    }

    @Test
    void fixedNotionalConvertsUsdtToBaseQuantity() {
        assertEquals(new BigDecimal("0.00500000"), size(FIXED_NOTIONAL, "5000", "100000", null, "500", null));
    }

    @Test
    void equityPercentUsesCurrentEquity() {
        assertEquals(new BigDecimal("0.01000000"), size(EQUITY_PERCENT, "5000", "100000", null, null, "0.2"));
    }

    @Test
    void oppositeSignalClosesCurrentPositionQuantity() {
        BacktestAccount account = new BacktestAccount(new BigDecimal("5000"), new BigDecimal("0.001"));
        account.execute(Signal.BUY, new BigDecimal("100000"), new BigDecimal("0.01000000"), 1L);

        BigDecimal quantity = new DefaultPositionSizer().size(Signal.SELL, account, new BigDecimal("101000"),
                BacktestConfig.builder()
                        .sizingMode(EQUITY_PERCENT)
                        .equityPercent(new BigDecimal("0.2"))
                        .build());

        assertEquals(new BigDecimal("0.01000000"), quantity);
    }

    private BigDecimal size(PositionSizingMode mode, String equity, String price, String quantity,
                            String notional, String percent) {
        BacktestAccount account = new BacktestAccount(new BigDecimal(equity), BigDecimal.ZERO);
        BacktestConfig config = BacktestConfig.builder()
                .sizingMode(mode)
                .orderQuantity(quantity == null ? null : new BigDecimal(quantity))
                .orderNotional(notional == null ? null : new BigDecimal(notional))
                .equityPercent(percent == null ? null : new BigDecimal(percent))
                .build();
        return new DefaultPositionSizer().size(Signal.BUY, account, new BigDecimal(price), config);
    }
}
