package com.quant.common.context;

/**
 * 通过 ThreadLocal 传递当前操作的 symbol，供 DynamicTableNameInnerInterceptor 路由 K 线分表。
 * 使用方式：KlineContext.set("btcusdt") → 执行 SQL → KlineContext.clear()
 */
public final class KlineContext {

    private static final ThreadLocal<String> SYMBOL = new ThreadLocal<>();

    private KlineContext() {}

    public static void set(String symbol) {
        SYMBOL.set(symbol.toLowerCase().replace("/", "").replace("-", ""));
    }

    public static String get() {
        return SYMBOL.get();
    }

    public static void clear() {
        SYMBOL.remove();
    }
}
