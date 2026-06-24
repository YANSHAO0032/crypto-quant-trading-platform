# Crypto Quant Trading Platform

基于 Spring Boot 3 的加密货币量化交易平台，支持实时行情订阅、多策略信号生成、五重风控管理、订单全生命周期管理及历史回测。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2.5、Java 17 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8.0 |
| 网络 | OkHttp 4.12（WebSocket + REST） |
| JSON | Fastjson2 2.0 |
| 构建 | Maven 多模块 |

---

## 模块结构

```
crypto-quant-trading-platform
├── quant-common        # 公共模型、枚举（Order、Position、Trade、TickData、InOutOrder、KlineRange …）
├── quant-market        # 行情层：Binance WebSocket 实时订阅、Tick 持久化、K 线范围索引
├── quant-strategy      # 策略层：双均线、RSI、网格三种内置策略，完整生命周期钩子
├── quant-risk          # 风控层：五重校验 + WalletManager 资金闭环
├── quant-oms           # 订单管理：IOrderManager 接口 + 实盘/回测双实现 + 策略事件分发
├── quant-execution     # 执行层：对接 Binance REST API 下单/撤单
├── quant-backtest      # 回测引擎：历史 CSV 或模拟数据驱动策略，输出含 Sharpe/Sortino 的绩效报告
├── quant-api           # REST 接口层（Spring MVC Controller）
└── quant-app           # 启动入口、application.yml、schema.sql、ProductionScheduler
```

---

## 架构数据流

```
Binance WebSocket
       ↓
  Market Data Layer        — Tick 落库 + KlineRange 范围索引更新 + TickReceivedEvent 心跳
       ↓
  Strategy Engine          — 双均线 / RSI / 网格 → BUY / SELL / HOLD 信号
                              onTick / onCheckExit / onOrderChange 三路回调
       ↓
  Risk Engine              — ① 参数校验 ② 仓位限制 ③ 下单频率
                              ④ 单笔金额 ⑤ USDT 可用余额（WalletManager）
       ↓
  OMS（订单状态机）         — PENDING → SUBMITTED → FILLED / REJECTED
                              资金冻结 / 解冻闭环，策略级持仓限额
       ↓
  Execution Engine         — Binance REST API 下单 / 撤单，状态变更实时落库

  ProductionScheduler      — 仓位对账(5min) / 行情超时检测(1min)
                              全局止损告警(5min) / 钱包快照(1h)
```

---

## 数据库表

| 表名 | 说明 |
|------|------|
| `quant_order` | 订单表，记录订单全生命周期 |
| `quant_position` | 仓位表，按交易对维护当前持仓量 |
| `quant_trade` | 成交记录表，每笔成交写入一条 |
| `quant_inout_order` | 进出一体交易记录，开/平仓绑定，含已实现盈亏和持仓时长 |
| `tick_data` | 行情 Tick 数据表，支持多周期 |
| `kline_range` | K 线数据范围索引，记录每个 symbol+interval 的起止时间戳 |
| `backtest_report` | 回测报告表，记录绩效指标（含 Sharpe/Sortino） |
| `backtest_trade_record` | 回测交易信号明细表 |

初始化 SQL 见 [`quant-app/src/main/resources/db/schema.sql`](quant-app/src/main/resources/db/schema.sql)。

---

## 快速启动

### 1. 前置条件

- JDK 17+
- Maven 3.8+
- MySQL 8.0（本地或 Docker）

### 2. 建库

```sql
CREATE DATABASE `quant-trading-platform` DEFAULT CHARACTER SET utf8mb4;
```

执行 `quant-app/src/main/resources/db/schema.sql` 初始化所有表。

### 3. 配置

编辑 `quant-app/src/main/resources/application.yml`，按需修改数据库连接和 Binance API 密钥：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/quant-trading-platform?...
    username: root
    password: your_password

binance:
  api-key: ${BINANCE_API_KEY:}      # 建议通过环境变量注入
  secret-key: ${BINANCE_SECRET_KEY:}
```

也可通过环境变量注入：

```bash
export BINANCE_API_KEY=your_api_key
export BINANCE_SECRET_KEY=your_secret_key
```

### 4. 编译运行

```bash
mvn clean package -DskipTests
java -jar quant-app/target/quant-app-1.0.0-SNAPSHOT.jar
```

或在 IDE 中直接运行 `com.quant.app.QuantApplication`。

---

## REST API

服务默认监听 `http://localhost:8080`。

### 订单接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/quant/orders` | 查询全部订单 |
| GET | `/api/quant/orders/{orderId}` | 查询单笔订单 |
| POST | `/api/quant/orders` | 创建订单 |
| POST | `/api/quant/orders/{orderId}/submit` | 提交订单（触发五重风控） |
| POST | `/api/quant/orders/{orderId}/cancel` | 取消订单（自动解冻资金） |

### 策略接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/quant/strategies` | 列出所有策略及运行状态 |

### 回测接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/quant/backtest/{strategyId}` | 运行快速回测（模拟数据） |

回测请求参数（Query String）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `symbol` | `BTCUSDT` | 交易对 |
| `dataCount` | `1000` | 模拟 Tick 条数 |
| `startPrice` | `65000` | 起始价格 |
| `capital` | `100000` | 初始资金（USDT） |

示例：

```bash
curl -X POST "http://localhost:8080/api/quant/backtest/MA_CROSS" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"BTCUSDT","dataCount":2000,"capital":100000}'
```

Kline backtest supports capital-based sizing:

```bash
curl -X POST "http://localhost:8080/api/quant/backtest/PRO_RSI_MR/kline" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol":"BTCUSDT",
    "interval":"1m",
    "startMs":"1767196800000",
    "endMs":"1782230400000",
    "capital":5000,
    "sizingMode":"EQUITY_PERCENT",
    "equityPercent":0.2,
    "allowPartialData":true,
    "timezone":"Asia/Shanghai"
  }'
```

`sizingMode` values:

- `FIXED_QTY`: use `orderQuantity`; backward-compatible default is `0.001`.
- `FIXED_NOTIONAL`: use `orderNotional / price`.
- `EQUITY_PERCENT`: use `currentEquity * equityPercent / price`.

When `allowPartialData=false`, kline backtests reject incomplete data coverage instead of silently truncating the requested range.

策略 ID：`MA_CROSS`（双均线）、`RSI`、`GRID`（网格）

---

## 内置策略

### 双均线交叉（MA_CROSS）
- 短周期 MA(5) 上穿长周期 MA(20) → **买入**
- 短周期 MA(5) 下穿长周期 MA(20) → **卖出**

### RSI 策略（RSI）
- RSI(14) < 30 超卖 → **买入**
- RSI(14) > 70 超买 → **卖出**

### 网格策略（GRID）
- 在 `[lowerPrice, upperPrice]` 区间等间距划分网格
- 价格下穿网格线 → **买入**，上穿网格线 → **卖出**
- 默认区间 60000–70000，10 格，可通过配置覆盖：

```yaml
strategy:
  grid:
    upper-price: 70000
    lower-price: 60000
    grid-count: 10
```

### 自定义策略开发

实现 `Strategy` 接口，覆盖所需钩子：

```java
public class MyStrategy implements Strategy {

    @Override
    public Signal onTick(TickData tick) {
        // 主逻辑：每个 Tick 返回交易信号
        return Signal.HOLD;
    }

    @Override
    public Signal onCheckExit(TickData tick) {
        // 追踪止损、条件止盈等出场逻辑（每 Tick 触发）
        return Signal.HOLD;
    }

    @Override
    public void onOrderChange(Order order, OrderChangeType changeType) {
        // 感知自己下的单是否成交，changeType：NEW/ENTER_FILL/EXIT_FILL/CANCELLED/REJECTED
    }
}
```

---

## 风控体系

### 五重校验（按顺序逐层过滤）

| 层级 | 校验内容 | 配置项 |
|------|---------|--------|
| ① | 订单参数合法性（symbol/price/qty/side 非空） | — |
| ② | 单品种最大持仓量 | `risk.max-position` |
| ③ | 每分钟最大下单次数（按策略 ID 计，每分钟自动重置） | `risk.max-order-per-minute` |
| ④ | 单笔订单金额上限 | `risk.max-order-amount` |
| ⑤ | USDT 可用余额充足性（WalletManager 实时校验） | — |

### 策略级持仓限额

```yaml
order:
  max-position-per-strategy: 3   # 每个策略最多同时持有 3 笔开仓订单
```

### 资金流转闭环

```
下单提交  →  freeze(USDT, price × qty)   可用余额减少，冻结余额增加
撤单      →  unfreeze(USDT, price × qty) 冻结余额归还可用
成交      →  confirmTrade()              冻结扣除，目标资产 available 增加
```

### 全部风控配置

```yaml
risk:
  max-order-amount: 100000       # 单笔最大金额（USDT）
  max-position: 10               # 单品种最大持仓量
  max-order-per-minute: 60       # 每分钟最大下单次数
  fatal-loss-rate: 0.2           # 全局止损阈值（账户亏损 20% 时告警）
  kline-timeout-seconds: 120     # 行情心跳超时秒数
```

---

## 回测绩效指标

`PerformanceAnalyzer` 输出以下指标：

| 指标 | 说明 |
|------|------|
| `totalReturn` | 总收益率 |
| `annualizedReturn` | 年化收益率（按实际交易天数折算 365 天） |
| `winRate` | 胜率 |
| `maxDrawdown` | 最大回撤 |
| `profitFactor` | 盈亏比（总盈利 / 总亏损） |
| `sharpeRatio` | 年化夏普比率（无风险利率取 0） |
| `sortinoRatio` | 年化索提诺比率（仅惩罚下行波动） |
| `dailyStats` | 按日 PnL 及日收益率明细列表 |

---

## 生产定时任务

`ProductionScheduler` 内置四个生产级监控任务：

| 任务 | 周期 | 说明 |
|------|------|------|
| 仓位对账 | 5 分钟 | 输出本地持仓快照，对接交易所后可做差值告警 |
| 行情超时检测 | 1 分钟 | 订阅 symbol 超过 `kline-timeout-seconds` 无行情则告警 |
| 全局止损检查 | 5 分钟 | 账户亏损率超过 `fatal-loss-rate` 时输出 ERROR 级告警 |
| 钱包快照日志 | 1 小时 | 记录所有资产的 available/frozen 余额快照 |

行情超时检测通过 `TickReceivedEvent` Spring 事件触发，与行情层完全解耦。

---

## 关键类说明

```
quant-common/
└── model/   Order, Position, Trade, TickData, BacktestReport
             InOutOrder   — 进出一体记录，含 realizedPnl 和持仓时长计算
             KlineRange   — K 线数据范围索引，回测前快速判断数据是否充足
└── enums/   OrderSide, OrderStatus, OrderType, Signal

quant-market/
└── BinanceMarketDataService   Tick 持久化 + KlineRange upsert + TickReceivedEvent 发布
└── BinanceWebSocketClient     WebSocket 连接 + 断线 5 秒自动重连
└── TickReceivedEvent          Spring 应用事件，解耦心跳更新

quant-strategy/
└── Strategy（接口）           onTick / onCheckExit / onOrderChange 三路钩子
└── impl/MovingAverageStrategy, RsiStrategy, GridStrategy

quant-risk/
└── RiskEngine       五重风控入口
└── RiskChecker      参数 / 仓位 / 频率 / 金额校验（每分钟 @Scheduled 重置计数）
└── PositionManager  仓位读写（ON DUPLICATE KEY UPDATE）
└── WalletManager    available/frozen 余额分离，freeze/unfreeze/confirmTrade 闭环

quant-oms/
└── IOrderManager        统一回测 / 实盘接口
└── OrderManager         实盘实现：数据库持久化 + 资金冻结解冻
└── BacktestOrderManager 回测实现：纯内存模拟撮合，SUBMITTED 立即 FILLED
└── OrderStateMachine    合法状态转换定义
└── OrderRepository      MyBatis-Plus 封装（insert or update）
└── OrderService         策略级持仓限额 + onOrderChange 事件分发 + onExitFill 回调

quant-execution/
└── ExecutionService        订单发送 & 撤单，状态变更实时落库
└── BinanceExchangeClient   Binance REST 客户端（API Key 由配置注入）

quant-backtest/
└── BacktestEngine      事务性驱动回放，结果写库
└── DataLoader          CSV 加载 & 模拟数据生成
└── PerformanceAnalyzer Sharpe / Sortino / 年化收益 / 按日分组统计

quant-app/
└── QuantApplication      启动入口
└── ProductionScheduler   仓位对账 / 行情超时 / 全局止损 / 钱包快照四个定时任务
```

---

## License

MIT
