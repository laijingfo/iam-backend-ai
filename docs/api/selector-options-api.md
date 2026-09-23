# 业务下拉选项接口

## 接口信息

- 请求方式：`GET`
- URL：`/itsApplication/selectorOptions`
- 返回值：下拉选项数组

## 请求参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `scene` | 是 | 业务场景枚举值 |
| `field` | 是 | 下拉字段枚举值 |
| `currentDataCycle` | 看板场景必填 | 周期 ID |

## scene 枚举及调用场景

| scene 值 | 调用场景 | 支持的 field                 |
| --- | --- |---------------------------|
| `normal` | 审核通知管理、审核结果查询与下载 | `cmdbId`、`appName`        |
| `integrationData` | 应用接入数据查询 | `cmdbId`、`appName`        |
| `alertDashboard` | 告警看板 | `cmdbId`、`appName` |
| `alertInvalidBpo` | BPO 失效告警 | `cmdbId`、`appName`、`operationOwner`、`operationFocal`        |
| `dashboard` | 看板 | 全部字段                      |

## field 枚举

| field 值           | 含义        |
|-------------------|-----------|
| `cmdbId`          | 应用编号      |
| `appName`         | 应用名称      |
| `operationDomain` | 运维 Domain |
| `operationTower`  | 运维 Tower  |
| `operationOwner`  | 运维负责人     |
| `operationFocal`  | S&A       |

## URL Demo

### 审核结果查询与下载——应用编号

```http
GET /itsApplication/selectorOptions?scene=normal&field=cmdbId
```

### 应用接入数据查询——应用名称

```http
GET /itsApplication/selectorOptions?scene=integrationData&field=appName
```

### 告警看板——应用编号

```http
GET /itsApplication/selectorOptions?scene=alertDashboard&field=cmdbId
```

### BPO 失效告警——应用名称

```http
GET /itsApplication/selectorOptions?scene=alertInvalidBpo&field=appName
```

### 看板——指定周期的运维 Domain

```http
GET /itsApplication/selectorOptions?scene=dashboard&field=operationDomain&currentDataCycle=周期ID
```

看板场景同样可以将 `field` 替换为：

```text
cmdbId
appName
operationDomain
operationTower
operationOwner
operationFocal
```

## 注意事项

- `dashboard` 场景必须传 `currentDataCycle`，当前周期或归档周期由后端自动判断。
- 非 `dashboard` 场景不需要传 `currentDataCycle`。
- 场景不支持指定字段时，接口会返回业务错误。
- 下拉数据已按当前登录人的业务数据权限过滤。
