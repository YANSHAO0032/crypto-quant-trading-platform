package com.quant.backtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.BacktestReport;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BacktestReportMapper extends BaseMapper<BacktestReport> {
}
