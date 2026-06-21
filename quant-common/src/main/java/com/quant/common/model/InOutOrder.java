package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.quant.common.enums.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 进出一体订单：将开仓和对应的平仓绑定为一笔完整交易，便于统计单笔盈亏和持仓时长。
 * 对应 banbot InOutOrder 设计思路。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quant_inout_order")
public class InOutOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 开仓订单 ID */
    private String enterOrderId;

    /** 平仓订单 ID（未平仓时为 null） */
    private String exitOrderId;

    /** 策略 ID */
    private String strategyId;

    /** 交易对 */
    private String symbol;

    /** 方向 */
    private OrderSide side;

    /** 开仓价格 */
    private BigDecimal enterPrice;

    /** 开仓数量 */
    private BigDecimal enterQty;

    /** 开仓时间 */
    private LocalDateTime enterTime;

    /** 平仓价格（未平仓为 null） */
    private BigDecimal exitPrice;

    /** 平仓数量（未平仓为 null） */
    private BigDecimal exitQty;

    /** 平仓时间（未平仓为 null） */
    private LocalDateTime exitTime;

    /** 已实现盈亏（平仓后计算） */
    private BigDecimal realizedPnl;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 是否已平仓 */
    private boolean closed;

    /**
     * 平仓并计算盈亏。
     */
    public void close(String exitOrderId, BigDecimal exitPrice, BigDecimal exitQty, LocalDateTime exitTime) {
        this.exitOrderId = exitOrderId;
        this.exitPrice = exitPrice;
        this.exitQty = exitQty;
        this.exitTime = exitTime;
        this.closed = true;
        this.realizedPnl = calcPnl();
    }

    /**
     * 计算已实现盈亏（多头：exitPrice - enterPrice；空头反向）。
     */
    private BigDecimal calcPnl() {
        if (enterPrice == null || exitPrice == null || enterQty == null) return BigDecimal.ZERO;
        BigDecimal qty = exitQty != null ? exitQty : enterQty;
        BigDecimal priceDiff = side == OrderSide.BUY
                ? exitPrice.subtract(enterPrice)
                : enterPrice.subtract(exitPrice);
        return priceDiff.multiply(qty).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 持仓时长（秒），仅平仓后有意义。
     */
    public long holdingSeconds() {
        if (enterTime == null || exitTime == null) return 0;
        return java.time.Duration.between(enterTime, exitTime).getSeconds();
    }
}
