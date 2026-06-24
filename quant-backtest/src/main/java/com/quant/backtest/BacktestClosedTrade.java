package com.quant.backtest;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BacktestClosedTrade {

    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal quantity;
    private BigDecimal grossPnl;
    private BigDecimal fee;
    private BigDecimal netPnl;
    private Long entryTimestampMs;
    private Long exitTimestampMs;
}
