# Crypto Quant Trading Platform

基于 Spring Boot 3 和 Java 17 的加密货币量化交易平台，当前主要覆盖四条链路：Binance 行情数据落库、策略信号运行、订单/钱包/仓位管理，以及带资金模型的历史 K 线回测。

## 技术栈

当前项目是 Maven 多模块单体应用，启动入口在 `quant-app`。服务端接口使用 Spring MVC，数据访问使用 MyBatis-Plus，历史行情主要存放在 MySQL 分表中。

| 层 | 技术 |
| --- | --- |
| 应用框架 | Spring Boot 3.2.5 |
| 语言版本 | Java 17 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8 |
| 交易所接入 | Binance REST / WebSocket |
| 构建 | Maven 多模块 |
| 测试 | JUnit 5 / Mockito / Spring Test |

运行和测试建议固定使用 JDK 17；本地如果默认 `java` 是 11，需要先设置 `JAVA_HOME`。

## 模块

模块之间按业务边界拆分，`quant-app` 聚合启动，`quant-api` 暴露 REST，核心回测逻辑集中在 `quant-backtest`。

| 模块 | 说明 |
| --- | --- |
| `quant-common` | 公共模型、枚举、K 线分表上下文 |
| `quant-market` | Binance 行情、Tick/Kline 持久化、K 线批量导入 |
| `quant-strategy` | 内置策略和策略运行器 |
| `quant-risk` | 风控、仓位、钱包管理 |
| `quant-oms` | 订单管理和进出场记录 |
| `quant-execution` | Binance 执行层 |
| `quant-backtest` | 回测引擎、账户模拟、仓位 sizing、绩效分析 |
| `quant-api` | REST API 和请求 DTO |
| `quant-app` | 启动入口、配置、数据库 schema、启动迁移、定时任务 |

`quant-backtest` 不直接依赖 Controller 请求对象；API 层会把请求体转换成 `BacktestConfig` 后传给引擎。

## 快速启动

默认激活 `dev` profile，数据库连接读取 `application-dev.yml`。第一次启动前需要先建库并执行 schema；旧库启动时会自动补齐新增回测报告列。

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

启动后可以先访问：

```http
GET /api/quant/strategies
```

如果返回策略列表，说明 API、策略注册和 Spring 容器已经正常。

## K 线数据

K 线主路径使用 `kline_{symbol}` 分表，例如 `kline_btcusdt`。查询时通过 `KlineContext` 和 MyBatis-Plus 动态表名插件路由到对应分表。

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

注意：

- 导入服务目前不会在写入时统一时间单位。
- 读时兼容只解决“能读到混合单位数据”和“按归一化时间排序”的问题。
- 回测的数据完整性由 `DataCoverageValidator` 在请求区间和实际区间之间做覆盖校验。

## 策略

策略实现 `Strategy` 接口，核心职责是根据 `TickData` 输出方向信号。策略不负责下单数量、手续费、资金占用和权益计算。

当前内置策略 ID：

| ID | 策略 |
| --- | --- |
| `MA_CROSS` | 双均线交叉 |
| `RSI` | RSI 超买超卖 |
| `GRID` | 网格策略 |
| `PRO_RSI_MR` | RSI + 均值回归 |

策略接口只输出方向信号：`BUY`、`SELL`、`HOLD`。实际回测下单数量由回测引擎的仓位 sizing 模块计算。

`PRO_RSI_MR` 内部维护 `FLAT/LONG/SHORT` 状态：空仓时按 RSI 和均值偏离开仓，持仓后价格回到均线附近时退出。回测账户会根据当前仓位解释 `BUY`/`SELL` 是开仓还是平仓。

## 回测接口

当前推荐使用标准 K 线回测接口。它会走真实 K 线数据、读时兼容、账户撮合、手续费、权益曲线、数据覆盖校验和绩效分析。

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

报告会持久化到 `backtest_report`，信号级交易记录会写入 `backtest_trade_record`。接口响应会额外包含不入库的 `dailyStats`。

## 回测账户和绩效逻辑

回测引擎现在不再只用固定 `strategy.order-quantity` 表示所有资金使用方式。标准 K 线回测可以通过请求体选择仓位 sizing，并用账户当前权益动态计算下单数量。

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

当前最大回撤基于每根 K 线 mark-to-market 后的权益点计算；日报按请求中的 `timezone` 分组，默认 `Asia/Shanghai`。

## 订单和策略运行接口

这些接口面向运行时交易和账户状态，不等同于回测成交明细。回测结果请优先看 `/api/quant/backtest` 相关接口。

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

`/trades/{strategyId}/open` 和 `/closed` 读取的是 OMS 的进出场记录，不是 `BacktestClosedTrade` 内存对象。

## 关键配置

下面是和当前逻辑直接相关的主要配置项。回测接口请求体中的 sizing 参数优先决定单次回测的仓位计算；没有传时才使用默认固定数量。

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

`backtest.fee-rate` 会进入 `BacktestAccount`，影响每笔成交后的现金、总手续费、闭合交易净盈亏和最终权益。

## 测试

项目已有回测资金模型、时间兼容、覆盖校验、绩效精度和 Controller 请求体测试。修改回测或接口时，至少跑全量测试一次。

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

打包验证：

```powershell
mvn -q -pl quant-app -am package -DskipTests
```

## 当前限制

这些限制来自当前代码实现，不是设计目标：

- 回测空头允许模拟开仓，但还没有保证金、杠杆、强平和资金费率模型。
- `backtest_trade_record` 目前保存信号级明细；完整 fill、closed trade、equity curve 仍主要在内存计算中使用，尚未完整持久化。
- 标准 K 线回测的覆盖校验只检查头尾区间缺口；区间内部缺失需要后续结合更细的连续性索引或逐 bar 校验。
- `kline_range` 已有表和部分更新逻辑，但历史 K 线批量导入当前主要依赖实际 K 线表查询和读时校验。
- 年化收益当前按总收益和自然日数量线性折算，不是复利 CAGR。

## License

MIT

当前仓库代码用于本地量化研究和系统验证；对接实盘前需要补齐交易权限、风控阈值、保证金模型和异常恢复策略。
