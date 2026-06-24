package com.quant.api.request;

import com.quant.common.enums.OrderSide;
import com.quant.common.enums.OrderType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

    private String symbol;
    private OrderSide side;
    private OrderType type;
    private BigDecimal price;
    private BigDecimal quantity;
    private String strategyId;
}
