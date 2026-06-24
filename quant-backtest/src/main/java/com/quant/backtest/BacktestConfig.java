package com.quant.backtest;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BacktestConfig {

    private PositionSizingMode sizingMode;
    private BigDecimal orderQuantity;
    private BigDecimal orderNotional;
    private BigDecimal equityPercent;
    private BigDecimal feeRate;
    private boolean allowPartialData;
    private String timezone;
}
