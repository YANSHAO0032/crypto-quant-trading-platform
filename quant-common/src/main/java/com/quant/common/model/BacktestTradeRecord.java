package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.quant.common.enums.Signal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 回测交易信号记录模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_trade_record")
public class BacktestTradeRecord {

    /** 记录ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 回测ID。 */
    private String backtestId;

    /** 序号。 */
    private Integer sequenceNo;

    /** 交易信号。 */
    private Signal signal;

    /** 信号价格。 */
    private BigDecimal price;

    /** 交易所事件时间戳。 */
    @TableField("event_timestamp")
    private Long timestamp;
}
