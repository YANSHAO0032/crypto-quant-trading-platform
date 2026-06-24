# Crypto Quant Trading Platform

基于 Spring Boot 3 和 Java 17 的加密货币量化交易平台，当前包含行情导入、策略运行、订单/账户/仓位管理和历史 K 线回测能力。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 应用框架 | Spring Boot 3.2.5 |
| 语言版本 | Java 17 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8 |
| 交易所接入 | Binance REST / WebSocket |
| 构建 | Maven 多模块 |

## 模块

| 模块 | 说明 |
| --- | --- |
| `quant-common` | 公共模型、枚举和上下文 |
| `quant-market` | Binance 行情、Tick/Kline 持久化、K 线导入 |
| `quant-strategy` | 内置策略和策略运行器 |
| `quant-risk` | 风控、仓位、钱包管理 |
| `quant-oms` | 订单管理和进出场记录 |
| `quant-execution` | Binance 执行层 |
| `quant-backtest` | 回测引擎、账户模拟、绩效分析 |
| `quant-api` | REST API |
| `quant-app` | 启动入口、配置、数据库 schema、启动迁移 |

## 快速启动

### 1. 准备环境

- JDK 17+
- Maven 3.8+
- MySQL 8

本地默认开发库配置在 `quant-app/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3309/quant-trading-platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 3196278
```

### 2. 初始化数据库

```sql
CREATE DATABASE `quant-trading-platform` DEFAULT CHARACTER SET utf8mb4;
```

执行：

```text
quant-app/src/main/resources/db/schema.sql
```

应用启动时还会运行 `BacktestReportSchemaMigrator`，幂等补齐 `backtest_report` 的新增回测审计字段，兼容旧库。

### 3. 编译和运行

```powershell
$env:JAVA_HOME='C:\Users\10703\.jdks\ms-17.0.19'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -q test
mvn -q -pl quant-app -am package -DskipTests
java -jar quant-app/target/quant-app-1.0.0-SNAPSHOT.jar
```

默认端口：`http://localhost:8080`

## K 线数据

### 导入 Binance 历史 K 线

接口：

```http
POST /api/kline/import
```

请求体：

```json
{
  "dataDir": "D:\\java_workspace\\btcdata",
  "symbol": "BTCUSDT",
  "interval": "1m"
}
```

当前导入逻辑会把 CSV/ZIP 中的 `open_time`、`close_time` 原样写入 `kline_{symbol}` 分表。Binance 历史文件可能混用毫秒、微秒或纳秒时间戳，因此回测读取侧做了兼容：

- 13 位：毫秒，例如 `1577836800000`
- 16 位：微秒，例如 `1780236300000000`
- 19 位：纳秒

`KlineMapper.xml` 会同时查询毫秒、微秒、纳秒区间；`MarketTimeNormalizer` 会在 Java 侧统一归一化到毫秒，并按归一化后的 `openTime` 排序、去重。

## 策略

当前内置策略 ID：

| ID | 策略 |
| --- | --- |
| `MA_CROSS` | 双均线交叉 |
| `RSI` | RSI 超买超卖 |
| `GRID` | 网格策略 |
| `PRO_RSI_MR` | RSI + 均值回归 |

策略接口只输出方向信号：`BUY`、`SELL`、`HOLD`。实际回测下单数量由回测引擎的仓位 sizing 模块计算。

## 回测接口

### 标准 K 线回测

接口：

```http
POST /api/quant/backtest/{strategyId}/kline
```

示例：

```bash
curl --location 'http://localhost:8080/api/quant/backtest/PRO_RSI_MR/kline' \
  --header 'Content-Type: application/json' \
  --data '{
    "symbol": "BTCUSDT",
    "interval": "1m",
    "startMs": "1767196800000",
    "endMs": "1782230400000",
    "capital": 5000,
    "sizingMode": "EQUITY_PERCENT",
    "equityPercent": 0.2,
    "allowPartialData": true,
    "timezone": "Asia/Shanghai"
  }'
```

请求字段：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `symbol` | `BTCUSDT` | 交易对 |
| `interval` | `1m` | K 线周期 |
| `startMs` | 无 | 请求开始时间戳，毫秒 |
| `endMs` | 无 | 请求结束时间戳，毫秒 |
| `capital` | `100000` | 初始资金 |
| `sizingMode` | `FIXED_QTY` | 仓位计算模式 |
| `orderQuantity` | `0.001` | 固定数量模式的下单数量 |
| `orderNotional` | 无 | 固定名义金额模式的每笔金额 |
| `equityPercent` | 无 | 权益百分比模式的使用比例 |
| `allowPartialData` | `false` | 是否允许数据不完整时继续回测 |
| `timezone` | `Asia/Shanghai` | 日收益统计时区 |

### 仓位 sizing 模式

| 模式 | 计算方式 | 适用场景 |
| --- | --- | --- |
| `FIXED_QTY` | 使用 `orderQuantity` | 兼容旧逻辑，默认 `0.001` BTC |
| `FIXED_NOTIONAL` | `orderNotional / price` | 每笔固定投入金额 |
| `EQUITY_PERCENT` | `currentEquity * equityPercent / price` | 更接近真实资金使用 |

反向信号会优先按当前仓位数量平仓，避免退出仓位时再次按权益比例重新计算数量。

### 数据覆盖校验

标准 K 线回测会比较请求区间和实际返回 K 线区间：

- `allowPartialData=false`：数据不完整时返回 `400`
- `allowPartialData=true`：继续回测，并在报告中返回 `coverageComplete=false`、`missingBars`、`coverageMessage`

例如请求到 2026-06-24，但库里只有 2026-05-31 之前的数据时，接口会返回类似：

```json
{
  "success": false,
  "message": "kline coverage incomplete: requested=[1767196800000,1782230400000], actual=[1767196800000,1780271999999], missingBars=32640"
}
```

### 最新 K 线回测

接口：

```http
POST /api/quant/backtest/{strategyId}/kline/latest
```

请求体：

```json
{
  "symbol": "BTCUSDT",
  "interval": "1m",
  "limit": 500,
  "capital": 100000
}
```

### 快速回测

接口：

```http
POST /api/quant/backtest/{strategyId}
```

请求体：

```json
{
  "symbol": "BTCUSDT",
  "dataCount": 2000,
  "startPrice": 65000,
  "capital": 100000
}
```

该接口优先从 `tick_data` 读取历史数据；如果没有数据，会生成模拟数据。

### 查询回测报告

```http
GET /api/quant/backtest?strategyId=PRO_RSI_MR&limit=20
```

## 回测账户和绩效逻辑

当前回测账户模型：

- 每根 K 线都会执行 mark-to-market，形成连续权益曲线。
- 每笔成交会影响 `cash`、`positionQty`、均价、手续费和已实现盈亏。
- 多头买入会检查现金是否足够覆盖名义金额和手续费。
- 空头开仓目前允许模拟，不做保证金和杠杆约束。
- 手续费率来自 `backtest.fee-rate`，默认 `0.001`。
- 初始权益点会写入权益曲线，避免最大回撤漏掉初始资金点。

绩效分析基于权益曲线和闭合交易计算：

| 字段 | 说明 |
| --- | --- |
| `initialCapital` / `finalCapital` | 初始和最终权益 |
| `totalReturn` | 总收益率 |
| `annualizedReturn` | 按日数折算的年化收益 |
| `totalTrades` | 闭合交易数量 |
| `winRate` | 胜率 |
| `maxDrawdown` | 基于连续权益曲线的最大回撤 |
| `profitFactor` | 盈亏比 |
| `sharpeRatio` | 基于日收益的年化 Sharpe |
| `sortinoRatio` | 基于下行波动的 Sortino |
| `dailyStats` | 按 `timezone` 分组的每日 PnL 和日收益 |
| `coverageComplete` / `missingBars` | 数据覆盖情况 |
| `sizingMode` / `equityPercent` / `totalFee` | 回测审计字段 |

Sharpe/Sortino 的均值和方差计算不会在中间步骤截断到 8 位，避免小收益策略被误算为 0。

## 订单和策略运行接口

### 订单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/quant/orders` | 查询订单 |
| `GET` | `/api/quant/orders/{orderId}` | 查询单笔订单 |
| `POST` | `/api/quant/orders` | 创建订单 |
| `POST` | `/api/quant/orders/{orderId}/submit` | 提交订单 |
| `POST` | `/api/quant/orders/{orderId}/cancel` | 取消订单 |

创建订单示例：

```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "type": "LIMIT",
  "price": 65000,
  "quantity": 0.01,
  "strategyId": "MANUAL"
}
```

### 策略运行

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/quant/strategies` | 查询策略列表 |
| `POST` | `/api/quant/strategies/{strategyId}/start` | 启动策略 |
| `POST` | `/api/quant/strategies/{strategyId}/stop` | 停止策略 |

启动策略请求体：

```json
{
  "symbol": "BTCUSDT"
}
```

### 仓位、钱包和交易记录

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/quant/positions` | 查询全部仓位 |
| `GET` | `/api/quant/positions/{symbol}` | 查询单交易对仓位 |
| `GET` | `/api/quant/wallet` | 查询钱包快照 |
| `GET` | `/api/quant/wallet/{asset}` | 查询单资产余额 |
| `GET` | `/api/quant/trades/{strategyId}/open` | 查询未平仓进出场记录 |
| `GET` | `/api/quant/trades/{strategyId}/closed` | 查询已平仓进出场记录 |
| `GET` | `/api/quant/trades/{strategyId}/pnl` | 查询策略已实现盈亏 |

## 关键配置

```yaml
strategy:
  order-quantity: 0.001
  rsi:
    period: 14
    oversold: 30
    overbought: 70
  ma:
    short-period: 5
    long-period: 20

backtest:
  fee-rate: 0.001

risk:
  max-order-amount: 100000
  max-position: 10
  max-order-per-minute: 60
  fatal-loss-rate: 0.2
  kline-timeout-seconds: 120
```

## 测试

```powershell
$env:JAVA_HOME='C:\Users\10703\.jdks\ms-17.0.19'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -q test
```

常见局部测试：

```powershell
mvn -q -pl quant-backtest -am test -Dtest=BacktestEngineSizingIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -q -pl quant-api -am test -Dtest=QuantControllerRequestBodyTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 当前限制

- 回测空头允许模拟开仓，但还没有保证金、杠杆、强平和资金费率模型。
- `backtest_trade_record` 目前保存信号级明细；完整 fill、closed trade、equity curve 仍主要在内存计算中使用，尚未完整持久化。
- 标准 K 线回测的覆盖校验只检查头尾区间缺口；区间内部缺失需要后续结合更细的连续性索引或逐 bar 校验。

## License

MIT
