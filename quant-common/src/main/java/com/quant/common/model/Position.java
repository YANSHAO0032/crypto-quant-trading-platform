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
 * 仓位模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quant_position")
public class Position {

    /** 交易对。 */
    @TableId
    private String symbol;

    /** 当前仓位，正数为多头，负数为空头。 */
    private BigDecimal quantity;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
