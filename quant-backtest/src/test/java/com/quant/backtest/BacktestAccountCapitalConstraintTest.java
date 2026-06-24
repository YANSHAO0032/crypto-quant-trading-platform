package com.quant.backtest;

import com.quant.common.enums.Signal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestAccountCapitalConstraintTest {

    @Test
    void buyCannotSpendMoreThanCashIncludingFee() {
        BacktestAccount account = new BacktestAccount(new BigDecimal("100"), new BigDecimal("0.001"));

        assertThrows(IllegalArgumentException.class,
                () -> account.execute(Signal.BUY, new BigDecimal("100000"), new BigDecimal("0.002"), 1L));
    }

    @Test
    void accountStartsWithInitialEquityPoint() {
        BacktestAccount account = new BacktestAccount(new BigDecimal("5000"), new BigDecimal("0.001"));

        account.markInitialEquity(1_767_196_800_000L);

        assertEquals(new BigDecimal("5000.00000000"), account.getEquityCurve().get(0).getEquity());
        assertEquals(new BigDecimal("5000.00000000"), account.getEquityCurve().get(0).getCash());
        assertEquals(BigDecimal.ZERO.setScale(8), account.getEquityCurve().get(0).getPositionQty());
    }
}
