package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.TickData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TickDataMapper extends BaseMapper<TickData> {
}
