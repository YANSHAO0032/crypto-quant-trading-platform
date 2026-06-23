package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * K线数据实体，对应 kline_{symbol} 分表。
 * 表名由 KlineContext 通过 DynamicTableNameInnerInterceptor 在运行时注入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kline_btcusdt")
public class Kline {

    /** K线开盘时间戳(ms)，联合主键之一，用 @TableField 避免 MyBatis-Plus 误当单主键处理 */
    @TableField("open_time")
    private Long openTime;

    /** K线周期，如 1m/5m/1h/4h/1d */
    private String interval;

    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;

    /** 成交量（标的币种） */
    private BigDecimal volume;

    /** K线收盘时间戳(ms) */
    private Long closeTime;

    /** 成交额(USDT) */
    private BigDecimal quoteVolume;

    /** 成交笔数 */
    private Integer tradeCount;
}
