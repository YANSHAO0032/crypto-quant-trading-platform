package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tick行情数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tick_data")
public class TickData {

    /** 数据ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交易对 */
    private String symbol;

    /** 数据周期 */
    private String interval;

    /** 最新价 */
    private BigDecimal lastPrice;

    /** 买一价 */
    private BigDecimal bidPrice;

    /** 买一量 */
    private BigDecimal bidQty;

    /** 卖一价 */
    private BigDecimal askPrice;

    /** 卖一量 */
    private BigDecimal askQty;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 最高价 */
    private BigDecimal highPrice;

    /** 最低价 */
    private BigDecimal lowPrice;

    /** 成交量 */
    private BigDecimal volume;

    /** 成交额 */
    private BigDecimal quoteVolume;

    /** 时间戳 */
    @TableField("event_timestamp")
    private long timestamp;

    /** 本地接收时间 */
    private LocalDateTime receiveTime;
}
