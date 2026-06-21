package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.KlineRange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KlineRangeMapper extends BaseMapper<KlineRange> {

    /**
     * 插入或更新数据范围（每次写入 Tick 后扩展区间）。
     */
    @Update("""
            INSERT INTO kline_range (symbol, `interval`, start_ms, end_ms, `count`, continuous)
            VALUES (#{symbol}, #{interval}, #{timestampMs}, #{timestampMs}, 1, true)
            ON DUPLICATE KEY UPDATE
                start_ms  = LEAST(start_ms, #{timestampMs}),
                end_ms    = GREATEST(end_ms, #{timestampMs}),
                `count`   = `count` + 1
            """)
    int upsertRange(@Param("symbol") String symbol,
                    @Param("interval") String interval,
                    @Param("timestampMs") long timestampMs);
}
