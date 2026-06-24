package com.quant.api.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuickBacktestRequest {

    private String symbol = "BTCUSDT";
    private Integer dataCount = 1000;
    private BigDecimal startPrice = new BigDecimal("65000");
    private BigDecimal capital = new BigDecimal("100000");
}
