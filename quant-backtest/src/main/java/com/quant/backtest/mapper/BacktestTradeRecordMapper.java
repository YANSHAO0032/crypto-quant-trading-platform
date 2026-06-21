package com.quant.backtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.BacktestTradeRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BacktestTradeRecordMapper extends BaseMapper<BacktestTradeRecord> {
}
