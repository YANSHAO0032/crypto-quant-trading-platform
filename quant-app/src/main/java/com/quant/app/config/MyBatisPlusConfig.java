package com.quant.app.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.quant.common.context.KlineContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        DynamicTableNameInnerInterceptor dynamicTableName = new DynamicTableNameInnerInterceptor();
        // 所有以 kline_ 开头的表都走动态路由
        dynamicTableName.setTableNameHandler((sql, tableName) -> {
            if (tableName.startsWith("kline_") && KlineContext.get() != null) {
                return "kline_" + KlineContext.get();
            }
            return tableName;
        });

        interceptor.addInnerInterceptor(dynamicTableName);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
