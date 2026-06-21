package com.quant.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回测每日统计（非持久化，随 BacktestReport 一起返回）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestDailyStats {

    private LocalDate date;
    private BigDecimal pnl;
    private BigDecimal returnRate;
}
