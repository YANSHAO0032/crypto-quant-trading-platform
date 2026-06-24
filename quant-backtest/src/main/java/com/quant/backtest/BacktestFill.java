package com.quant.backtest;

import com.quant.common.enums.Signal;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BacktestFill {

    private Signal signal;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal notional;
    private BigDecimal fee;
    private Long timestampMs;
    private BigDecimal cashAfter;
    private BigDecimal positionQtyAfter;
}
