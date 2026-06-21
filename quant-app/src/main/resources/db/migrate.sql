-- =============================================================
-- 增量 DDL：对应 banbot 借鉴改造后的数据库变更
-- 执行前请确保已初始化 schema.sql（全量建表脚本）
-- =============================================================

-- ------------------------------------------------------------
-- 1. backtest_report 表新增三个绩效指标字段
--    对应 PerformanceAnalyzer 新增 annualizedReturn / sharpeRatio / sortinoRatio
-- ------------------------------------------------------------
ALTER TABLE backtest_report
    ADD COLUMN annualized_return DECIMAL(36, 18) NULL COMMENT '年化收益率' AFTER profit_factor,
    ADD COLUMN sharpe_ratio      DECIMAL(36, 18) NULL COMMENT '夏普比率'   AFTER annualized_return,
    ADD COLUMN sortino_ratio     DECIMAL(36, 18) NULL COMMENT '索提诺比率' AFTER sharpe_ratio;

-- ------------------------------------------------------------
-- 2. 新增 kline_range 表
--    记录每个 symbol+interval 的数据起止时间戳，供回测前快速判断数据是否充足
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kline_range (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    symbol     VARCHAR(32) NOT NULL COMMENT '交易对',
    `interval` VARCHAR(16) NOT NULL COMMENT 'K线周期',
    start_ms   BIGINT      NOT NULL COMMENT '数据起始时间戳(ms)',
    end_ms     BIGINT      NOT NULL COMMENT '数据结束时间戳(ms)',
    `count`    BIGINT      NOT NULL DEFAULT 0 COMMENT '数据条数',
    continuous TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否连续完整',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kline_range_symbol_interval (symbol, `interval`),
    KEY idx_kline_range_time (start_ms, end_ms)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='K线数据范围索引表';

-- ------------------------------------------------------------
-- 3. 新增 quant_inout_order 表
--    将开仓订单与平仓订单绑定为一笔完整交易，记录已实现盈亏和持仓时长
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quant_inout_order (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    enter_order_id VARCHAR(64)  NOT NULL COMMENT '开仓订单ID',
    exit_order_id  VARCHAR(64)  NULL COMMENT '平仓订单ID',
    strategy_id    VARCHAR(128) NULL COMMENT '策略ID',
    symbol         VARCHAR(32)  NOT NULL COMMENT '交易对',
    side           VARCHAR(16)  NOT NULL COMMENT '方向',
    enter_price    DECIMAL(36, 18) NULL COMMENT '开仓价格',
    enter_qty      DECIMAL(36, 18) NULL COMMENT '开仓数量',
    enter_time     DATETIME(3)  NULL COMMENT '开仓时间',
    exit_price     DECIMAL(36, 18) NULL COMMENT '平仓价格',
    exit_qty       DECIMAL(36, 18) NULL COMMENT '平仓数量',
    exit_time      DATETIME(3)  NULL COMMENT '平仓时间',
    realized_pnl   DECIMAL(36, 18) NULL COMMENT '已实现盈亏',
    create_time    DATETIME(3)  NULL COMMENT '创建时间',
    closed         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已平仓',
    PRIMARY KEY (id),
    KEY idx_inout_strategy_symbol (strategy_id, symbol),
    KEY idx_inout_enter_order (enter_order_id),
    KEY idx_inout_closed_time (closed, enter_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='进出一体交易记录表';

-- ------------------------------------------------------------
-- 4. 新增 quant_trade 表
--    每笔成交写入一条，关联 quant_order
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quant_trade (
    trade_id         VARCHAR(64)  NOT NULL COMMENT '成交ID',
    order_id         VARCHAR(64)  NOT NULL COMMENT '关联内部订单ID',
    symbol           VARCHAR(32)  NOT NULL COMMENT '交易对',
    side             VARCHAR(16)  NOT NULL COMMENT '成交方向',
    price            DECIMAL(36, 18) NOT NULL COMMENT '成交价格',
    quantity         DECIMAL(36, 18) NOT NULL COMMENT '成交数量',
    commission       DECIMAL(36, 18) NULL COMMENT '手续费',
    commission_asset VARCHAR(16)  NULL COMMENT '手续费币种',
    trade_time       DATETIME(3)  NOT NULL COMMENT '成交时间',
    is_maker         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为maker',
    PRIMARY KEY (trade_id),
    KEY idx_quant_trade_order_id (order_id),
    KEY idx_quant_trade_symbol_time (symbol, trade_time),
    CONSTRAINT fk_quant_trade_order
        FOREIGN KEY (order_id) REFERENCES quant_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='成交记录表';
