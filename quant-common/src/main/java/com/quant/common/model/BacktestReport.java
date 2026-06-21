package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回测绩效报告模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_report")
public class BacktestReport {

    /** 回测ID。 */
    @TableId
    private String backtestId;

    /** 策略ID。 */
    private String strategyId;

    /** 策略名称。 */
    private String strategyName;

    /** 交易对。 */
    private String symbol;

    /** 数据条数。 */
    private Integer dataCount;

    /** 初始资金。 */
    private BigDecimal initialCapital;

    /** 最终资金。 */
    private BigDecimal finalCapital;

    /** 总收益率。 */
    private BigDecimal totalReturn;

    /** 总交易次数。 */
    private Integer totalTrades;

    /** 盈利次数。 */
    private Integer winCount;

    /** 亏损次数。 */
    private Integer lossCount;

    /** 胜率。 */
    private BigDecimal winRate;

    /** 最大回撤。 */
    private BigDecimal maxDrawdown;

    /** 盈亏比。 */
    private BigDecimal profitFactor;

    /** 开始时间。 */
    private LocalDateTime startTime;

    /** 结束时间。 */
    private LocalDateTime endTime;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
