package com.quant.backtest;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EquityPoint {

    private Long timestampMs;
    private BigDecimal equity;
    private BigDecimal cash;
    private BigDecimal positionQty;
    private BigDecimal markPrice;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal totalFee;
}
