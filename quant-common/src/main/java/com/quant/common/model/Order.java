package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.quant.common.enums.OrderSide;
import com.quant.common.enums.OrderStatus;
import com.quant.common.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quant_order")
public class Order {

    /** 内部订单ID */
    @TableId
    private String orderId;

    /** 交易所订单ID */
    private String exchangeOrderId;

    /** 交易对 (e.g., BTCUSDT) */
    private String symbol;

    /** 订单方向 */
    private OrderSide side;

    /** 订单类型 */
    private OrderType type;

    /** 订单状态 */
    private OrderStatus status;

    /** 委托价格 */
    private BigDecimal price;

    /** 委托数量 */
    private BigDecimal quantity;

    /** 已成交数量 */
    private BigDecimal filledQuantity;

    /** 平均成交价格 */
    private BigDecimal avgFilledPrice;

    /** 策略ID */
    private String strategyId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;
}
