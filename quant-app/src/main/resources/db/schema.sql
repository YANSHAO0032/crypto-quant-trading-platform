CREATE TABLE IF NOT EXISTS quant_order (
    order_id VARCHAR(64) NOT NULL COMMENT '内部订单ID',
    exchange_order_id VARCHAR(128) NULL COMMENT '交易所订单ID',
    symbol VARCHAR(32) NULL COMMENT '交易对',
    side VARCHAR(16) NULL COMMENT '订单方向',
    type VARCHAR(16) NULL COMMENT '订单类型',
    status VARCHAR(32) NULL COMMENT '订单状态',
    price DECIMAL(36,18) NULL COMMENT '委托价格',
    quantity DECIMAL(36,18) NULL COMMENT '委托数量',
    filled_quantity DECIMAL(36,18) NULL COMMENT '已成交数量',
    avg_filled_price DECIMAL(36,18) NULL COMMENT '平均成交价格',
    strategy_id VARCHAR(128) NULL COMMENT '策略ID',
    create_time DATETIME(3) NULL COMMENT '创建时间',
    update_time DATETIME(3) NULL COMMENT '更新时间',
    remark VARCHAR(512) NULL COMMENT '备注',
    PRIMARY KEY (order_id),
    KEY idx_quant_order_exchange_order_id (exchange_order_id),
    KEY idx_quant_order_symbol_create_time (symbol, create_time),
    KEY idx_quant_order_strategy_id_create_time (strategy_id, create_time),
    KEY idx_quant_order_status_create_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS quant_position (
    symbol VARCHAR(32) NOT NULL COMMENT '交易对',
    quantity DECIMAL(36,18) NOT NULL DEFAULT 0 COMMENT '当前仓位',
    create_time DATETIME(3) NULL COMMENT '创建时间',
    update_time DATETIME(3) NULL COMMENT '更新时间',
    PRIMARY KEY (symbol),
    KEY idx_quant_position_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓位表';

CREATE TABLE IF NOT EXISTS tick_data (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据ID',
    symbol VARCHAR(32) NOT NULL COMMENT '交易对',
    interval VARCHAR(16) NOT NULL DEFAULT 'TICK' COMMENT '数据周期',
    last_price DECIMAL(36,18) NULL COMMENT '最新价',
    bid_price DECIMAL(36,18) NULL COMMENT '买一价',
    bid_qty DECIMAL(36,18) NULL COMMENT '买一量',
    ask_price DECIMAL(36,18) NULL COMMENT '卖一价',
    ask_qty DECIMAL(36,18) NULL COMMENT '卖一量',
    open_price DECIMAL(36,18) NULL COMMENT '开盘价',
    high_price DECIMAL(36,18) NULL COMMENT '最高价',
    low_price DECIMAL(36,18) NULL COMMENT '最低价',
    volume DECIMAL(36,18) NULL COMMENT '成交量',
    quote_volume DECIMAL(36,18) NULL COMMENT '成交额',
    event_timestamp BIGINT NOT NULL COMMENT '交易所事件时间戳',
    receive_time DATETIME(3) NULL COMMENT '本地接收时间',
    PRIMARY KEY (id),
    KEY idx_tick_data_symbol_interval_time (symbol, interval, event_timestamp),
    KEY idx_tick_data_symbol_time (symbol, event_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行情数据表';

CREATE TABLE IF NOT EXISTS quant_trade (
    trade_id VARCHAR(64) NOT NULL COMMENT '成交ID',
    order_id VARCHAR(64) NOT NULL COMMENT '关联内部订单ID',
    symbol VARCHAR(32) NOT NULL COMMENT '交易对',
    side VARCHAR(16) NOT NULL COMMENT '成交方向',
    price DECIMAL(36,18) NOT NULL COMMENT '成交价格',
    quantity DECIMAL(36,18) NOT NULL COMMENT '成交数量',
    commission DECIMAL(36,18) NULL COMMENT '手续费',
    commission_asset VARCHAR(16) NULL COMMENT '手续费币种',
    trade_time DATETIME(3) NOT NULL COMMENT '成交时间',
    is_maker TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为maker',
    PRIMARY KEY (trade_id),
    KEY idx_quant_trade_order_id (order_id),
    KEY idx_quant_trade_symbol_time (symbol, trade_time),
    CONSTRAINT fk_quant_trade_order
        FOREIGN KEY (order_id) REFERENCES quant_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成交记录表';

CREATE TABLE IF NOT EXISTS backtest_report (
    backtest_id VARCHAR(64) NOT NULL COMMENT '回测ID',
    strategy_id VARCHAR(128) NULL COMMENT '策略ID',
    strategy_name VARCHAR(128) NULL COMMENT '策略名称',
    symbol VARCHAR(32) NULL COMMENT '交易对',
    data_count INT NULL COMMENT '数据条数',
    initial_capital DECIMAL(36,18) NULL COMMENT '初始资金',
    final_capital DECIMAL(36,18) NULL COMMENT '最终资金',
    total_return DECIMAL(36,18) NULL COMMENT '总收益率',
    total_trades INT NULL COMMENT '总交易次数',
    win_count INT NULL COMMENT '盈利次数',
    loss_count INT NULL COMMENT '亏损次数',
    win_rate DECIMAL(36,18) NULL COMMENT '胜率',
    max_drawdown DECIMAL(36,18) NULL COMMENT '最大回撤',
    profit_factor DECIMAL(36,18) NULL COMMENT '盈亏比',
    start_time DATETIME(3) NULL COMMENT '开始时间',
    end_time DATETIME(3) NULL COMMENT '结束时间',
    create_time DATETIME(3) NULL COMMENT '创建时间',
    PRIMARY KEY (backtest_id),
    KEY idx_backtest_report_strategy_time (strategy_id, create_time),
    KEY idx_backtest_report_symbol_time (symbol, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测报告表';

CREATE TABLE IF NOT EXISTS backtest_trade_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    backtest_id VARCHAR(64) NOT NULL COMMENT '回测ID',
    sequence_no INT NOT NULL COMMENT '序号',
    signal VARCHAR(16) NOT NULL COMMENT '交易信号',
    price DECIMAL(36,18) NULL COMMENT '信号价格',
    event_timestamp BIGINT NOT NULL COMMENT '交易所事件时间戳',
    PRIMARY KEY (id),
    UNIQUE KEY uk_backtest_trade_record_seq (backtest_id, sequence_no),
    KEY idx_backtest_trade_record_backtest_id (backtest_id),
    CONSTRAINT fk_backtest_trade_record_report
        FOREIGN KEY (backtest_id) REFERENCES backtest_report (backtest_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测交易信号记录表';
