package com.quant.api.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KlineBacktestRequest {

    private String symbol = "BTCUSDT";
    private String interval = "1m";
    private Long startMs;
    private Long endMs;
    private BigDecimal capital = new BigDecimal("100000");
    private String sizingMode = "FIXED_QTY";
    private BigDecimal orderQuantity = new BigDecimal("0.001");
    private BigDecimal orderNotional;
    private BigDecimal equityPercent;
    private Boolean allowPartialData = false;
    private String timezone = "Asia/Shanghai";
}
