package com.quant.backtest;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class BacktestRunResult {

    private BigDecimal initialCapital;
    private List<BacktestFill> fills;
    private List<BacktestClosedTrade> closedTrades;
    private List<EquityPoint> equityCurve;
}
