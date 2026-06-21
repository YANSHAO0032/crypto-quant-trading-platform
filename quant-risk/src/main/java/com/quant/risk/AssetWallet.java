package com.quant.risk;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单资产钱包：维护可用余额与冻结余额。
 */
@Data
public class AssetWallet {

    private BigDecimal available;
    private BigDecimal frozen;

    public AssetWallet(BigDecimal available, BigDecimal frozen) {
        this.available = available;
        this.frozen = frozen;
    }
}
