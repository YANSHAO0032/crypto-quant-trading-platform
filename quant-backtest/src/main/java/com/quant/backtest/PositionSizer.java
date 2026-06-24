package com.quant.backtest;

import com.quant.common.enums.Signal;

import java.math.BigDecimal;

public interface PositionSizer {

    BigDecimal size(Signal signal, BacktestAccount account, BigDecimal price, BacktestConfig config);
}
