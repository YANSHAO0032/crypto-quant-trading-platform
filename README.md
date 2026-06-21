# Crypto Quant Trading Platform

基于 Spring Boot 3 的加密货币量化交易平台，支持实时行情订阅、多策略信号生成、风控管理、订单全生命周期管理及历史回测。

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
├── quant-common        # 公共模型、枚举（Order、Position、Trade、TickData …）
├── quant-market        # 行情层：Binance WebSocket 实时订阅、Tick 持久化
├── quant-strategy      # 策略层：双均线、RSI、网格三种内置策略
├── quant-risk          # 风控层：仓位、频率、单笔金额三重校验
├── quant-oms           # 订单管理：状态机、创建/提交/取消、成交回写
├── quant-execution     # 执行层：对接 Binance REST API 下单/撤单
├── quant-backtest      # 回测引擎：历史 CSV 或模拟数据驱动策略，输出绩效报告
├── quant-api           # REST 接口层（Spring MVC Controller）
└── quant-app           # 启动入口、application.yml、schema.sql
```

---

## 架构数据流

```
Binance WebSocket
       ↓
  Market Data Layer        — 实时 Tick 落库（tick_data）
       ↓
  Strategy Engine          — 双均线 / RSI / 网格 → BUY / SELL / HOLD 信号
       ↓
  Risk Engine              — 仓位限制 / 下单频率 / 单笔金额三重校验
       ↓
  OMS（订单状态机）         — PENDING → SUBMITTED → FILLED / REJECTED
       ↓
  Execution Engine         — Binance REST API 下单 / 撤单
```

---

## 数据库表

| 表名 | 说明 |
|------|------|
| `quant_order` | 订单表，记录订单全生命周期 |
| `quant_position` | 仓位表，按交易对维护当前持仓量 |
| `quant_trade` | 成交记录表，每笔成交写入一条 |
| `tick_data` | 行情 Tick 数据表，支持多周期 |
| `backtest_report` | 回测报告表，记录绩效指标 |
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
| POST | `/api/quant/orders/{orderId}/submit` | 提交订单（触发风控） |
| POST | `/api/quant/orders/{orderId}/cancel` | 取消订单 |

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
curl -X POST "http://localhost:8080/api/quant/backtest/MA_CROSS?symbol=BTCUSDT&dataCount=2000&capital=100000"
```

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

---

## 风控参数

```yaml
risk:
  max-order-amount: 100000   # 单笔最大金额（USDT）
  max-position: 10           # 单品种最大持仓量
  max-order-per-minute: 60   # 每分钟最大下单次数（按策略 ID 计）
```

---

## 关键类说明

```
quant-common/
└── model/          Order, Position, Trade, TickData, BacktestReport …
└── enums/          OrderSide, OrderStatus, OrderType, Signal

quant-market/
└── BinanceMarketDataService   行情订阅、Tick 持久化、内存最新价缓存
└── BinanceWebSocketClient     WebSocket 连接 + 断线自动重连

quant-strategy/
└── Strategy（接口）
└── impl/MovingAverageStrategy, RsiStrategy, GridStrategy

quant-risk/
└── RiskEngine        风控入口，串联各检查器
└── RiskChecker       参数校验 / 仓位 / 频率 / 金额（每分钟定时重置计数）
└── PositionManager   仓位读写（MySQL，ON DUPLICATE KEY UPDATE）

quant-oms/
└── OrderStateMachine  合法状态转换定义
└── OrderManager       创建 / 提交 / 取消核心逻辑
└── OrderRepository    MyBatis-Plus 封装（insert or update）
└── OrderService       面向策略的简化接口，onFill 同时写成交记录

quant-execution/
└── ExecutionService        订单发送 & 撤单，状态变更实时落库
└── BinanceExchangeClient   Binance REST 客户端（API Key 由配置注入）

quant-backtest/
└── BacktestEngine      驱动策略回放历史数据，结果事务写库
└── DataLoader          CSV 加载 & 模拟数据生成
└── PerformanceAnalyzer 胜率、最大回撤、盈亏比计算
```

---

## License

MIT
