package com.quant.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.Kline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KlineMapper extends BaseMapper<Kline> {

    /**
     * 按时间范围查询 K 线（表名由 DynamicTableNameInnerInterceptor 动态替换）。
     */
    @Select("""
            SELECT * FROM kline_btcusdt
            WHERE `interval` = #{interval}
              AND open_time >= #{startMs}
              AND open_time <= #{endMs}
            ORDER BY open_time ASC
            """)
    List<Kline> selectByRange(@Param("interval") String interval,
                              @Param("startMs") long startMs,
                              @Param("endMs") long endMs);

    /**
     * 查询指定 symbol+interval 最新的 N 根 K 线（降序取，调用方自行翻转）。
     */
    @Select("""
            SELECT * FROM kline_btcusdt
            WHERE `interval` = #{interval}
            ORDER BY open_time DESC
            LIMIT #{limit}
            """)
    List<Kline> selectLatest(@Param("interval") String interval,
                             @Param("limit") int limit);
}
