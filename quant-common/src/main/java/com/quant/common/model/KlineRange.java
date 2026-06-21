package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * K 线数据范围索引：记录每个 symbol+interval 组合在数据库中的起止时间戳范围。
 * 对应 banbot SRange 设计——回测前先查此表判断数据是否充足，避免全表扫描。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kline_range")
public class KlineRange {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交易对 */
    private String symbol;

    /** K 线周期，如 TICK/1m/5m/1h/1d */
    private String interval;

    /** 数据起始时间戳（ms） */
    private Long startMs;

    /** 数据结束时间戳（ms） */
    private Long endMs;

    /** 数据条数 */
    private Long count;

    /** 是否有连续完整数据（中途无缺失） */
    private boolean continuous;
}
