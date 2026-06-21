package com.quant.risk;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钱包管理器：维护每个资产的可用/冻结余额，实现资金流转闭环。
 *
 * 资金流转：
 *   下单时: available → frozen
 *   成交时: frozen → 扣除（买入则增加目标资产 available）
 *   撤单时: frozen → available（解冻）
 */
@Slf4j
@Component
public class WalletManager {

    private final Map<String, AssetWallet> wallets = new ConcurrentHashMap<>();

    public void init(String asset, BigDecimal available) {
        wallets.put(normalize(asset), new AssetWallet(available, BigDecimal.ZERO));
        log.info("钱包初始化: asset={}, available={}", asset, available);
    }

    public BigDecimal getAvailable(String asset) {
        return getOrCreate(asset).getAvailable();
    }

    public BigDecimal getFrozen(String asset) {
        return getOrCreate(asset).getFrozen();
    }

    public BigDecimal getTotal(String asset) {
        AssetWallet w = getOrCreate(asset);
        return w.getAvailable().add(w.getFrozen());
    }

    /**
     * 下单冻结：从可用余额扣除转入冻结。返回 false 表示余额不足。
     */
    public boolean freeze(String asset, BigDecimal amount) {
        AssetWallet w = getOrCreate(normalize(asset));
        synchronized (w) {
            if (w.getAvailable().compareTo(amount) < 0) {
                log.warn("余额不足，冻结失败: asset={}, available={}, required={}", asset, w.getAvailable(), amount);
                return false;
            }
            w.setAvailable(w.getAvailable().subtract(amount));
            w.setFrozen(w.getFrozen().add(amount));
            log.debug("资金冻结: asset={}, amount={}, available={}, frozen={}", asset, amount, w.getAvailable(), w.getFrozen());
            return true;
        }
    }

    /**
     * 撤单解冻：将冻结余额归还可用。
     */
    public void unfreeze(String asset, BigDecimal amount) {
        AssetWallet w = getOrCreate(normalize(asset));
        synchronized (w) {
            BigDecimal release = amount.min(w.getFrozen());
            w.setFrozen(w.getFrozen().subtract(release));
            w.setAvailable(w.getAvailable().add(release));
            log.debug("资金解冻: asset={}, amount={}, available={}, frozen={}", asset, release, w.getAvailable(), w.getFrozen());
        }
    }

    /**
     * 成交确认：从冻结余额扣除，增加目标资产可用余额。
     */
    public void confirmTrade(String costAsset, BigDecimal costAmount, String gainAsset, BigDecimal gainAmount) {
        AssetWallet costWallet = getOrCreate(normalize(costAsset));
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

    public boolean hasEnough(String asset, BigDecimal amount) {
        return getAvailable(asset).compareTo(amount) >= 0;
    }

    public Map<String, BigDecimal[]> snapshot() {
        Map<String, BigDecimal[]> snap = new ConcurrentHashMap<>();
        wallets.forEach((k, w) -> snap.put(k, new BigDecimal[]{w.getAvailable(), w.getFrozen()}));
        return snap;
    }

    private AssetWallet getOrCreate(String asset) {
        return wallets.computeIfAbsent(normalize(asset), k -> new AssetWallet(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private String normalize(String asset) {
        return asset == null ? "UNKNOWN" : asset.toUpperCase();
    }

    @Data
    private static class AssetWallet {
        private BigDecimal available;
        private BigDecimal frozen;

        AssetWallet(BigDecimal available, BigDecimal frozen) {
            this.available = available;
            this.frozen = frozen;
        }
    }
}
