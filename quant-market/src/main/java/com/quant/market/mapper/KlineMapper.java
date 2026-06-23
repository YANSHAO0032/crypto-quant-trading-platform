package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.Kline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KlineMapper extends BaseMapper<Kline> {

    List<Kline> selectByRange(@Param("interval") String interval,
                              @Param("startMs") long startMs,
                              @Param("endMs") long endMs);

    List<Kline> selectLatest(@Param("interval") String interval,
                             @Param("limit") int limit);
}
