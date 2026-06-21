package com.quant.app;

import com.quant.execution.binance.BinanceExchangeClient;
import com.quant.risk.WalletManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 启动时从 Binance 同步账户余额到 WalletManager。
 * 若 API Key 未配置或网络不可用，使用 yml 中的默认初始资金。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletInitializer implements ApplicationRunner {

    private final WalletManager walletManager;
    private final BinanceExchangeClient exchangeClient;

    @Value("${wallet.default-usdt:10000}")
    private BigDecimal defaultUsdt;

    @Value("${binance.api-key:}")
    private String apiKey;

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Binance API Key 未配置，使用默认初始资金: USDT={}", defaultUsdt);
            walletManager.init("USDT", defaultUsdt);
            return;
        }

        try {
            Map<String, BigDecimal> balances = exchangeClient.getAccountBalances();
            if (balances.isEmpty()) {
                log.warn("从 Binance 获取余额为空，使用默认初始资金");
                walletManager.init("USDT", defaultUsdt);
            } else {
                balances.forEach(walletManager::init);
                log.info("Binance 账户余额同步完成，资产数={}", balances.size());
            }
        } catch (Exception e) {
            log.error("从 Binance 同步账户余额失败，使用默认初始资金", e);
            walletManager.init("USDT", defaultUsdt);
        }
    }
}
