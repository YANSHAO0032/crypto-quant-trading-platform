package com.quant.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 全局线程池统一配置。
 * 新增业务线程池在此处声明，通过 @Qualifier 注入使用。
 */
@Configuration
public class AsyncConfig {

    /** K线历史数据导入，IO密集，4线程并发按月导入 */
    @Value("${kline.import.threads:4}")
    private int klineImportThreads;

    @Bean("klineImportExecutor")
    public ThreadPoolExecutor klineImportExecutor() {
        return buildExecutor(klineImportThreads, 256, "kline-import");
    }

    /**
     * 通用线程池构造，统一命名规范: {prefix}-{序号}
     *
     * @param threads   核心/最大线程数（IO密集建议 CPU*2，CPU密集建议 CPU+1）
     * @param queueSize 等待队列容量，超出后触发 CallerRunsPolicy 由调用方线程执行
     * @param prefix    线程名前缀
     */
    private ThreadPoolExecutor buildExecutor(int threads, int queueSize, String prefix) {
        var counter = new java.util.concurrent.atomic.AtomicInteger(1);
        return new ThreadPoolExecutor(
                threads,
                threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> new Thread(r, prefix + "-" + counter.getAndIncrement()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
