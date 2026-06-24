package com.quant.common.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回测绩效报告模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_report")
public class BacktestReport {

    /** 回测ID */
    @TableId
    private String backtestId;

    /** 策略ID */
    private String strategyId;

    /** 策略名称 */
    private String strategyName;

    /** 交易对 */
    private String symbol;

    /** K线周期，如 1m/5m/1h/4h/1d */
    @TableField("`interval`")
    private String interval;

    /** 回测数据起始时间戳(ms) */
    private Long rangeStartMs;

    /** 回测数据结束时间戳(ms) */
    private Long rangeEndMs;

    /** 用户请求的起始时间戳(ms) */
    private Long requestedStartMs;

    /** 用户请求的结束时间戳(ms) */
    private Long requestedEndMs;

    /** 实际数据是否完整覆盖请求区间 */
    private Boolean coverageComplete;

    /** 缺失的K线数量 */
    private Long missingBars;

    /** 数据覆盖说明 */
    private String coverageMessage;

    /** 数据条数 */
    private Integer dataCount;

    /** 仓位计算模式 */
    private String sizingMode;

    /** 固定下单数量 */
    private BigDecimal orderQuantity;

    /** 固定名义下单金额 */
    private BigDecimal orderNotional;

    /** 按权益百分比下单 */
    private BigDecimal equityPercent;

    /** 手续费率 */
    private BigDecimal feeRate;

    /** 总手续费 */
    private BigDecimal totalFee;

    /** 被拒绝的订单数量 */
    private Integer rejectedOrders;

    /** 初始资金 */
    private BigDecimal initialCapital;

    /** 最终资金 */
    private BigDecimal finalCapital;

    /** 总收益率 */
    private BigDecimal totalReturn;

    /** 总交易次数 */
    private Integer totalTrades;

    /** 盈利次数 */
    private Integer winCount;

    /** 亏损次数 */
    private Integer lossCount;

    /** 胜率 */
    private BigDecimal winRate;

    /** 最大回撤 */
    private BigDecimal maxDrawdown;

    /** 盈亏比 */
    private BigDecimal profitFactor;

    /** 年化收益率 */
    private BigDecimal annualizedReturn;

    /** 夏普比率 */
    private BigDecimal sharpeRatio;

    /** 索提诺比率 */
    private BigDecimal sortinoRatio;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 每日统计（不入库，随接口响应返回） */
    @TableField(exist = false)
    private List<BacktestDailyStats> dailyStats;
}
