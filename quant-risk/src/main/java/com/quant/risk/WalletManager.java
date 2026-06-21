package com.quant.risk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钱包管理器：维护每个资产的可用/冻结/挂单余额，实现资金流转闭环。
 *
 * 资金流转流程：
 *   下单时: available → frozen
 *   成交时: frozen → 确认出帐（买入则增加目标资产 available）
 *   撤单时: frozen → available（解冻）
 */
@Slf4j
@Component
public class WalletManager {

    private final Map<String, AssetWallet> wallets = new ConcurrentHashMap<>();

    /**
     * 初始化资产余额（仅初始化时调用一次）。
     */
    public void init(String asset, BigDecimal available) {
        wallets.put(normalize(asset), new AssetWallet(available));
        log.info("钱包初始化: asset={}, available={}", asset, available);
    }

    /**
     * 查询可用余额。
     */
    public BigDecimal getAvailable(String asset) {
        return getOrCreate(asset).getAvailable();
    }

    /**
     * 查询冻结余额（已下单未成交）。
     */
    public BigDecimal getFrozen(String asset) {
        return getOrCreate(asset).getFrozen();
    }

    /**
     * 查询总余额（可用 + 冻结）。
     */
    public BigDecimal getTotal(String asset) {
        AssetWallet w = getOrCreate(asset);
        return w.getAvailable().add(w.getFrozen());
    }

    /**
     * 下单冻结：从可用余额扣除，转入冻结余额。
     *
     * @return false 表示余额不足
     */
    public boolean freeze(String asset, BigDecimal amount) {
        String key = normalize(asset);
        AssetWallet w = getOrCreate(key);
        synchronized (w) {
            if (w.getAvailable().compareTo(amount) < 0) {
                log.warn("余额不足，冻结失败: asset={}, available={}, required={}",
                        asset, w.getAvailable(), amount);
                return false;
            }
            w.setAvailable(w.getAvailable().subtract(amount));
            w.setFrozen(w.getFrozen().add(amount));
            log.debug("资金冻结: asset={}, amount={}, available={}, frozen={}",
                    asset, amount, w.getAvailable(), w.getFrozen());
            return true;
        }
    }

    /**
     * 撤单解冻：将冻结余额归还可用余额。
     */
    public void unfreeze(String asset, BigDecimal amount) {
        String key = normalize(asset);
        AssetWallet w = getOrCreate(key);
        synchronized (w) {
            BigDecimal release = amount.min(w.getFrozen());
            w.setFrozen(w.getFrozen().subtract(release));
            w.setAvailable(w.getAvailable().add(release));
            log.debug("资金解冻: asset={}, amount={}, available={}, frozen={}",
                    asset, release, w.getAvailable(), w.getFrozen());
        }
    }

    /**
     * 成交确认：从冻结余额扣除已成交部分，增加目标资产可用余额。
     *
     * @param costAsset  支出资产（如 USDT）
     * @param costAmount 实际花费金额
     * @param gainAsset  获得资产（如 BTC）
     * @param gainAmount 获得数量
     */
    public void confirmTrade(String costAsset, BigDecimal costAmount,
                             String gainAsset, BigDecimal gainAmount) {
        String costKey = normalize(costAsset);
        AssetWallet costWallet = getOrCreate(costKey);
        synchronized (costWallet) {
            BigDecimal release = costAmount.min(costWallet.getFrozen());
            costWallet.setFrozen(costWallet.getFrozen().subtract(release));
        }

        AssetWallet gainWallet = getOrCreate(normalize(gainAsset));
        synchronized (gainWallet) {
            gainWallet.setAvailable(gainWallet.getAvailable().add(gainAmount));
        }

        log.info("成交确认: -{}{} +{}{}", costAmount, costAsset, gainAmount, gainAsset);
    }

    /**
     * 检查可用余额是否足够。
     */
    public boolean hasEnough(String asset, BigDecimal amount) {
        return getAvailable(asset).compareTo(amount) >= 0;
    }

    /**
     * 获取所有资产快照（用于日志/监控）。
     */
    public Map<String, BigDecimal[]> snapshot() {
        Map<String, BigDecimal[]> snap = new ConcurrentHashMap<>();
        wallets.forEach((k, w) -> snap.put(k, new BigDecimal[]{w.getAvailable(), w.getFrozen()}));
        return snap;
    }

    private AssetWallet getOrCreate(String asset) {
        return wallets.computeIfAbsent(normalize(asset), k -> new AssetWallet(BigDecimal.ZERO));
    }

    private String normalize(String asset) {
        return asset == null ? "UNKNOWN" : asset.toUpperCase();
    }

    /** 单资产钱包 */
    private static class AssetWallet {
        private BigDecimal available;
        private BigDecimal frozen;

        AssetWallet(BigDecimal available) {
            this.available = available;
            this.frozen = BigDecimal.ZERO;
        }

        BigDecimal getAvailable() { return available; }
        void setAvailable(BigDecimal v) { this.available = v; }
        BigDecimal getFrozen() { return frozen; }
        void setFrozen(BigDecimal v) { this.frozen = v; }
    }
}
