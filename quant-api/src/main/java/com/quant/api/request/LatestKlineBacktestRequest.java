package com.quant.api.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LatestKlineBacktestRequest {

    private String symbol = "BTCUSDT";
    private String interval = "1m";
    private Integer limit = 500;
    private BigDecimal capital = new BigDecimal("100000");
}
