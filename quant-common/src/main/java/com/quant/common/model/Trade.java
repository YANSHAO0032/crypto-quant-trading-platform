package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.quant.common.enums.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交记录模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quant_trade")
public class Trade {

    /** 成交ID */
    @TableId
    private String tradeId;

    /** 关联订单ID */
    private String orderId;

    /** 交易对 */
    private String symbol;

    /** 成交方向 */
    private OrderSide side;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private BigDecimal quantity;

    /** 手续费 */
    private BigDecimal commission;

    /** 手续费币种 */
    private String commissionAsset;

    /** 成交时间 */
    private LocalDateTime tradeTime;

    /** 是否为maker */
    private Boolean maker;
}
