package com.quant.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.common.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PositionMapper extends BaseMapper<Position> {

    @Update("""
            INSERT INTO quant_position (symbol, quantity, create_time, update_time)
            VALUES (#{symbol}, #{delta}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                quantity = quantity + #{delta},
                update_time = #{now}
            """)
    int addPosition(
            @Param("symbol") String symbol,
            @Param("delta") BigDecimal delta,
            @Param("now") LocalDateTime now);
}
