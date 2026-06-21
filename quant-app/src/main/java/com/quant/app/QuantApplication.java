package com.quant.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 加密货币量化交易平台启动类
 */
@SpringBootApplication(scanBasePackages = "com.quant")
@EnableScheduling
@EnableAsync
@MapperScan({
        "com.quant.oms.mapper",
        "com.quant.risk.mapper",
        "com.quant.market.mapper",
        "com.quant.backtest.mapper"
})
public class QuantApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantApplication.class, args);
    }
}
