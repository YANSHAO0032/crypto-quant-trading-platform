package com.quant.api.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyVO {
    private String strategyId;
    private String strategyName;
    private boolean running;
    private String symbol;
}
