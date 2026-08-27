# 席位凭证 OAuth 签发设计

**日期**：2026-08-27
**涉及仓库**：`mintpop-lane/apps/server`（主体）、`mintpop-lane-desktop`（两个字段）
**状态**：待评审

## 一、背景

现有席位凭证由管理员在本机跑 `claude setup-token` 产出，粘进后台。该命令固定请求 `scope=user:inference`，签发出的 token **不含 `user:profile`**。

后果：Claude Code 启动时请求 `/api/claude_cli/bootstrap` 被服务端 403（客户端日志 `[Bootstrap] Skipped: 403 for OAuth token without profile scope`），拿不到 `additional_model_options`。而 **Fable 5 等新模型并不在 CLI 内置模型表里，只由该字段下发** —— 于是席位会话的 `/model` 里永远看不到它。同理缺失的还有 `modelAccessCache`、`clientDataCache` 等一系列服务端下发数据。

scope 在签发时写死，事后无法补救；`CLAUDE_CODE_OAUTH_SCOPES` 只影响客户端本地判定，骗不过服务端。

## 二、已验证的关键事实

均经实测或源码核对，非推断：

1. **scope 与有效期是两个正交参数**，都在换取 token 时由客户端指定。`expires_in` 是 Anthropic token 端点接受的非标准扩展请求参数。`claude setup-token` 不过是把 `scope=user:inference` 与 `expires_in=31536000` 绑在了一起，二者无因果关系。

2. **服务端按 scope 逐个校验能否自定义有效期**。实测报错：`Custom expires_in not allowed for scope 'user:mcp_servers'`。白名单内含 `user:inference` 与 `user:profile`，不含 `user:mcp_servers`。

3. **`user:profile` + `user:inference` 可以拿到 365 天**。实测响应：

   ```
   expires_in             : 31536000  ≈ 365 天
   scope                  : user:inference user:profile
   access_token           : sk-ant-oat01…（108 字符）
   refresh_token          : sk-ant-ort01…（108 字符）
   refresh_token 有效期    : 2552692  ≈ 29.5 天
   其它字段                : issued_at, token_uuid
   ```

4. **refresh_token 反而是短期的**（29.5 天）。这是设计意图：access_token 既已一年，refresh_token 无需长存。故本方案**直接丢弃 refresh_token**，一年重签一次 —— 「refresh_token 是否轮换」这一问题在本方案下不涉及。

5. **凭证前缀 `sk-ant-oat01` 与 setup token 一致**，属同一类凭证。下发通道、存储结构、注入方式均无需改动。

6. **舍去的三个 scope 对本产品无实际影响**：`user:mcp_servers` 只管 claude.ai 账号侧的托管 connectors（席位账号归管理员，用户无从访问其网页端），CLI 源码明言 *locally-configured MCP servers in managed-mcp.json / .claude.json / .mcp.json are NOT affected*；`user:sessions:claude_code`（Remote Control）与 `user:file_upload` 均为加分项。

## 三、方案总览

由服务端签发席位凭证，取代管理员本机跑命令：

| | 现在 | 改后 |
|---|---|---|
| 谁签发 | 管理员本机 `claude setup-token` | 服务端，经该用户的落地节点出站 |
| scope | `user:inference` | `user:profile user:inference` |
| 有效期 | 固定 365 天 | **跟随订阅时长**（上限 365 天） |
| 出口 IP | 管理员家里 | 与该席位日常使用的出口**同一个** |
| 客户端 | 注入 `CLAUDE_CODE_OAUTH_TOKEN` | 同上，外加 `CLAUDE_CODE_OAUTH_SCOPES` |
| 提前失效 | 无手段 | 可主动吊销（假设待验证，见 §9.2） |

三条硬约束（评审已确认）：

- **链路未配置完整的用户，不得为其签发凭证。** 没有确定的出口，签出的 token 即来路不明。
- **签发全程走该席位日常使用的同一出口。** 签发地与使用地一致。
- **凭证有效期不得超过订阅剩余时长。** 用户可从会话环境变量取得凭证明文，有效期是压缩其价值窗口的唯一可靠手段。

## 四、落地节点协议收窄

### 4.1 决策

**`LAND` 角色的节点只允许 `SOCKS5` 与 `HTTP` 两种协议。**

服务端因此可用标准代理机制出站，**不引入 mihomo 实例**。

### 4.2 为何成立

链路是两跳：`客户端 → FRONT → LAND → 目标`（LAND 节点配 `dialer-proxy` 指向 FRONT）。两跳职责不同：

- `客户端 → FRONT`：穿越审查的一跳，需加密混淆协议，**协议集不变**
- `FRONT → LAND`：服务器之间，不承担抗审查职责
- `LAND → 目标`：提供干净的出口 IP

LAND 的全部价值是 `egress_ip`。抗审查责任始终在 FRONT。

### 4.3 连带影响

- **订阅导入的节点不能再做 LAND**（机场订阅几乎不提供 SOCKS5）。这实际上是把既有的隐含设计显式化了 —— LAND 带 `capacity`、`egress_ip`、`egress_timezone`，本就是精细管理的自建资源；共享节点的 IP 早已不干净。
- `MIHOMO` 协议（整份参数透传）天然排除在 LAND 之外。
- 存量数据无需迁移（评审确认无历史包袱）。

### 4.4 改动点

- `NodeProtocol` 新增 `HTTP`，`secretKeys = {username, password}`（与 `SOCKS5` 同）
- `NodeRole` 承载协议白名单，提供 `allows(NodeProtocol)` 判定：`FRONT` 全集，`LAND` 仅 `SOCKS5` / `HTTP`
- 校验落三处：节点保存（`NodeSaveRequest`）、订阅导入（强制 `role = FRONT`）、用户绑定落地节点时的防御性二次校验

## 五、数据模型

### 5.1 `subscription` 新增列

凭证本身仍存 `credential_cipher`（形态未变），补充元数据：

| 列 | 类型 | 说明 |
|---|---|---|
| `credential_scope` | varchar(200) | 服务端实际授予的 scope，空格分隔。**空表示旧式凭证**（inference-only） |
| `credential_token_uuid` | varchar(64) | Anthropic 侧 token 标识，审计与排障用 |
| `credential_issued_at` | timestamp | 签发时刻，取响应的 `issued_at` |
| `credential_expires_at` | timestamp | 到期时刻 = `issued_at + expires_in` |
| `credential_refresh_cipher` | varchar(500) | refresh_token 密文。**第一版不使用**，仅作后备与演进预留，见 §9.4 |

五列均可空。**空 `credential_scope` 即旧凭证**，客户端不注入 scope 变量，行为与今日完全一致 —— 这就是平滑过渡的机制，无需数据迁移，也无需一次性切换。

按项目规范：列名小写 snake_case，每列带中文注释。

### 5.2 新表 `oauth_session`

授权会话需跨两次 HTTP 请求。不用内存存储 —— 服务端可能多副本，且该表天然带审计价值。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `session_id` | varchar(64) | 会话标识，返回给前端 |
| `subscription_id` | bigint | 本次签发的目标订阅 |
| `code_verifier_cipher` | varchar(500) | PKCE verifier 密文，复用 `CredentialCipher` |
| `state` | varchar(100) | OAuth state |
| `scope` | varchar(200) | 本次请求的 scope |
| `created_at` / `expires_at` | timestamp | TTL 30 分钟 |

`code_verifier` 是敏感值，必须加密存储 —— 它与 code 组合即可换出 token。

## 六、签发流程

两个管理端接口，均按项目规范返回 `ApiResponse<T>`，失败用业务错误码。

### 6.1 生成授权链接

`POST /api/admin/subscriptions/{id}/credential/authorize-url`

1. 过签发守卫（见第七节），任一不过即返回对应业务错误码
2. 生成 `state`（32 字节）、`code_verifier`（32 字节 → base64url 无 padding）、`code_challenge`（S256）
3. 写入 `oauth_session`
4. 拼授权 URL —— 参数顺序与 CLI 一致，scope 内空格编码为 `+`：

   ```
   https://claude.com/cai/oauth/authorize
     ?code=true
     &client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e
     &response_type=code
     &redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback
     &scope=user:profile+user:inference
     &code_challenge=<challenge>
     &code_challenge_method=S256
     &state=<state>
   ```

5. 返回 `{ authUrl, sessionId, accountEmail, egressIp }`

   后两项供管理员**在授权前核对**：该登录哪个账号、出口是哪个。签错账号是这个流程最容易犯的错。

### 6.2 兑换凭证

`POST /api/admin/subscriptions/{id}/credential/exchange`，body `{ sessionId, code }`

1. 取 `oauth_session`，校验未过期、`subscription_id` 相符
2. 按 `authCode#state` 规则拆分 code
3. **经该用户的落地节点出站**，POST `https://platform.claude.com/v1/oauth/token`：

   ```json
   {
     "grant_type": "authorization_code",
     "code": "...", "state": "...",
     "client_id": "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
     "redirect_uri": "https://platform.claude.com/oauth/code/callback",
     "code_verifier": "...",
     "expires_in": 31536000
   }
   ```

   `expires_in` 的取值不是固定的 31536000，按订阅时长计算，见 §9.1。
   请求头 `User-Agent: axios/1.13.6`，与 CLI 一致。

4. **校验响应**，任一不符即拒绝落库并明确报错：

   - `scope` 必须含 `user:profile` —— 缺了它整个方案的目的就落空了
   - `expires_in` 必须 ≥ 300 天（25 920 000 秒）—— 取这个阈值而非严格等于 31536000，是为容忍上游做小幅调整；一旦掉到 8 小时量级（说明白名单策略变了）必然落在阈值之下，会被当场拦住

   上游随时可能收紧策略，**静默接受一个残缺凭证比签发失败更糟** —— 前者要等用户报「Fable 又不见了」才会发现。
5. 加密写入 `credential_cipher` 与各元数据列；refresh_token 一并加密存入 `credential_refresh_cipher`（第一版不使用，理由见 §9.4）
6. 删除 `oauth_session`
7. 返回 `{ accountEmail, grantedScope, expiresAt }` —— **不回传 token 本身**

### 6.3 出站客户端

新增 `LandProxyClientFactory`：输入落地节点 DTO，输出绑定该代理的 HTTP client。

**必须每次新建、不得共享**：不同席位的落地节点凭据不同。

⚠️ **不要用 `java.net.Proxy` + `Authenticator.setDefault()`** —— 那是 JVM 全局单例，多席位并发签发时认证信息会串。使用 OkHttp 的 `proxyAuthenticator`（per-client）或等价机制。

该工厂服务两处调用：出口 IP 实探、token 交换。

## 七、签发守卫

按序判定，任一不过即中止并返回对应业务错误码。顺序有意义 —— 越便宜、越根本的检查越靠前：

| # | 判定 | 不过时的语义 |
|---|---|---|
| 1 | 订阅存在且 `agentType == CLAUDE` | 非 Claude 席位无此流程 |
| 2 | 订阅已绑定用户 | 未分配的订阅无从确定出口 |
| 3 | 用户 `frontNodeId` 与 `landNodeId` 均非空 | **链路未配置完整** |
| 4 | 两节点 `status == ENABLED` | 链路当前不可用 |
| 5 | 落地节点协议 ∈ {`SOCKS5`, `HTTP`} | 该落地节点不支持服务端出站 |
| 6 | 落地节点 `egressIp` 非空 | 出口 IP 未录入，无法校验一致性 |
| 7 | **实探出口**：经该落地节点查询公网 IP，须等于 `egressIp` | 见下 |

第 7 条是本设计相对 mintpop-api 的净增优势 —— 后者不记录出口 IP，只能信任代理配置。

**第 7 条的失败必须分流**，两种情况用户能做的事完全不同（与既有 `LinkSituation` 的设计取向一致）：

- **探测失败**（超时、连不上）→ 落地节点当前不可达，属网络问题，建议重试
- **IP 不符** → 配置与实际不一致，属配置错误，必须人工核对后修正

二者混成一句报错会把网络抖动误报成配置错误。

## 八、客户端改动

改动量共两个字段。

1. **`LinkConfigResponse.AgentCredential`** 新增 `String credentialScope`
2. **`link/remote.rs`** 的 `AgentCredentialData` 对应新增字段，标 `#[serde(default)]` 退化为空串 —— 与既有 `assignmentNo` 的处理一致：少一个展示字段不该让整份链路配置解析失败
3. **`pty/agent.rs`** 的 `AgentSpec` 新增 `scope_env: Option<&'static str>`，`CLAUDE` 为 `Some("CLAUDE_CODE_OAUTH_SCOPES")`，`CODEX` 为 `None`
4. **`pty/session.rs`** 注入时：`scope_env` 存在**且** scope 非空才注入该变量

第 4 条的条件是平滑过渡的关键：旧凭证 scope 为空 → 不注入 → 行为与今日逐字相同。新旧席位可长期并存。

**不改动** `pty/onboarding.rs`。该 hack 仅在走 `claude auth login` 路径时才成为多余，本方案下发的是 access_token、不走那条路，`hasCompletedOnboarding` 仍需自行补写。

## 九、凭证时效与失效

要解决的问题：**用户可能只买一个月，但凭证签了一年**。订阅到期后他仍握有可用凭证。

⚠️ 前提认知：**用户能拿到凭证明文**。会话内一句 `env | grep CLAUDE` 就能看到完整 token，复制到任何机器都能用。环境变量注入这条路上无法防住这件事 —— 既有的 setup-token 方案同样如此，且是满 365 天。因此下面两层手段的目标不是「防止泄露」，而是**压缩泄露的价值窗口**。

### 9.1 有效期跟随订阅时长（主防线）

签发时按订阅剩余时间计算，而非固定一年：

```
expires_in = clamp(subscription.endsAt + 1 天 - now, 下限, 365 天)
```

- **上限 365 天**：已实测的服务端上限
- **+1 天缓冲**：时钟偏差或时区处理稍有出入，会让用户在订阅最后一天突然不可用 —— 那是可感知的故障，而多给一天的损失可忽略
- **下限**：`expires_in` 是否有服务端最小值尚未实测。实现上取 1 天作为保护，若上游拒绝过小的值需回退到其接受的最小值

于是：

| 订阅时长 | 凭证有效期 | 到期后敞口 |
|---|---|---|
| 1 个月 | ≈1 个月 | **0** |
| 1 年 | ≈1 年 | **0** |

**这一层是数学保证，不依赖任何运行时动作** —— 不需要定时任务跑成功、不需要退订钩子被触发、不需要网络调用成功。「用户到期后继续使用」在有效期跟随订阅之后不再成立。

**凭证到期日与订阅止期必须保持同步。**

当前系统没有独立的「续订」功能：套餐与时长在分配后不可改，`endsAt` 由服务端按快照时长随 `startsAt` 重算，换套餐则删除后重新分配。因此**事实上的续期就是改 `startsAt`**，而换套餐产生的是一条全新订阅（本就没有凭证，天然需要签发）。

所以触发点不是「续订事件」，而是 **`endsAt` 发生变化**。判定收在订阅更新流程（`AdminSubscriptionService.update`）里：

> 该订阅已有凭证（`credential_cipher` 非空）、且 `credential_expires_at` 与新的 `endsAt + 1 天` 偏差超过一天时，标记为「凭证待更新」。

两个方向都要管，因为都造成不一致：

- **凭证早于订阅到期**（起期后移）→ 用户续了期却会在老到期日突然断掉
- **凭证晚于订阅到期**（起期前移或缩短）→ 凭证超发，回到本节要解决的那个漏洞

后台在订阅列表与详情显著提示该状态；重签由管理员走一次 §6 的签发流程。

**手工录入凭证时必须清空全部元数据列**（`credential_scope`、`credential_token_uuid`、`credential_issued_at`、`credential_expires_at`、`credential_refresh_cipher`）。手工凭证来源不明、有效期未知，不得继承上一次签发的元数据 —— 否则会出现「元数据显示还有半年，实际凭证早已失效」的错觉。清空 `credential_scope` 后客户端自动退回旧式行为（不注入 scope 变量），语义正确。

### 9.2 吊销（补充手段）

用于**提前**终止的场景，有效期覆盖不到的部分：提前退订、席位回收或换绑、凭证疑似泄露、账号封禁。

```
POST https://platform.claude.com/v1/oauth/token/revoke
{
  "token": "<access_token>",
  "token_type_hint": "access_token",
  "client_id": "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
}
```

> ⚠️ **未验证假设**：CLI 只在登出时用 **refresh_token** 调用该端点。以 access_token 吊销是按 RFC 7009 推断的 —— 该规范规定 `token_type_hint` 只是提示，服务端按提示查不到时**应当继续在其它类型中查找**。**上线前必须验证**（见 §12 验收第 7 条）。

设计上不让方案依赖这个假设：

- 吊销**失败不阻塞业务**。退订该完成还是完成，吊销失败只记录并告警
- 吊销结果必须可观测，**不得静默吞掉** —— 否则「以为吊销了其实没有」比「知道没吊销」更危险
- 假设不成立时的退路见 §9.4

### 9.3 到期提醒

短订阅由 §9.1 自然覆盖，无需提醒；**长订阅需要**，因为第一版没有自动续期通道，一年后必须人工重新授权，忘记会导致席位静默失效。

- 后台订阅列表展示凭证剩余天数，临期高亮
- 每日定时任务扫描 `credential_expires_at`，在到期前 30 / 7 / 1 天通知管理员
- `credential_scope` 为空的旧式凭证不纳入提醒（其到期时间未知）
- 提醒与订阅到期提醒**分开**：凭证到期和订阅到期是两件事，合并会让管理员分不清该续费还是该重签

### 9.4 refresh_token：存而不用

第一版**不使用** refresh_token，但**加密存下来**。多一个字段的成本，换两件事：

**一是吊销假设的后备。** 若 §9.2 的 access_token 吊销被证伪，还可退回 CLI 已验证的路径 —— 以 refresh_token 吊销（RFC 7009 建议连带作废派生的 access_token）。受其 29.5 天寿命限制，但提前退订通常发生在订阅早期，多半仍在窗口内。

**二是短订阅自动续期的演进入口。** 月付用户若每月都要管理员手动授权一次，负担随用户数线性增长。而 refresh_token 的 29.5 天寿命恰好覆盖月付周期：续费时服务端可用它自动换发新凭证（换取请求同样支持指定 `expires_in`），无需管理员介入。

**第一版不做这件事**，因为它会把「定期刷新 + 轮换处理 + 并发锁」那一整套复杂度引回来，而这正是本方案刻意绕开的。等订阅时长分布明确、确认月付占比确实高时再启用 —— 届时字段已经在了。

### 9.5 残余风险

| 场景 | 是否覆盖 |
|---|---|
| 订阅正常到期后继续使用 | ✅ §9.1，敞口为零 |
| 提前退订 / 封禁 | ⚠️ 依赖 §9.2 吊销（假设待验证）；失败则退化为等待自然到期，用户最多多用「其已付费的剩余时间」 |
| 用户抠出凭证在别处使用 | ⚠️ 无法防止。价值上限被 §9.1 压到订阅剩余时长 |
| 凭证泄露给第三方 | ⚠️ 同上，另可经 §9.2 主动吊销 |

## 十、明确不做

- **不做自动续期**。理由见 §9.4。字段已预留，等订阅时长分布明确后再评估。
- **不做 sessionKey 全自动授权**。它需要管理员的 claude.ai 会话 cookie 进入服务端，且从数据中心 IP 使用住宅 IP 签发的 cookie 是典型会话劫持特征，风控敏感度远高于标准 token 端点。第一版只做手动 PKCE。
- **不做 token 吊销**。上游无公开吊销接口，且一年一换。
- **不移除现有手工录入入口**。两种方式并存，手工入口作为逃生口保留。
- **不改 FRONT 的协议集**。收窄只针对 LAND。

## 十一、风险与已知边界

| 风险 | 说明 | 处置 |
|---|---|---|
| 管理员授权那一跳不走链路出口 | 步骤 6.1 中管理员在自己浏览器打开授权页并登录，该次登录来自管理员网络，与后续使用地不一致 | 评审决定接受。授权是一次性事件，真实用户换网络登录本属常见 |
| 上游调整 scope 白名单或有效期上限 | `expires_in` 与 scope 组合的许可规则由上游掌握，可能变化 | 6.2 步骤 4 的响应校验会当场发现并拒绝落库，不会静默产出残缺凭证 |
| 长订阅到期后集体失效 | 第一版无自动续期 | §9.3 的到期提醒 |
| **access_token 吊销可能不被支持** | §9.2 的核心假设未经验证，仅由 RFC 7009 推断 | 主防线 §9.1 不依赖它；证伪后退回 refresh_token 吊销（字段已存）。**上线前必须验证** |
| 短订阅导致签发频繁 | 月付用户每月需管理员授权一次，负担随用户数线性增长 | 第一版接受；确认月付占比高后启用 §9.4 的自动续期 |
| 落地节点单点 | 一个落地节点承载 `capacity` 个用户，节点故障时这批用户既不能用也不能重签 | 既有风险，本方案不引入新增，但签发守卫第 4/7 条会让故障更早暴露 |

## 十二、验收

1. 为一个链路完整的 Claude 席位走完签发流程，`credential_scope` 落库为 `user:inference user:profile`，`credential_expires_at` **与该订阅的 `endsAt` 相差约一天**（而非固定一年）
2. 该席位起会话，客户端 debug 日志出现 `[Bootstrap] Fetch ok`（而非 `Skipped: 403`）
3. 会话内 `/model` 列表包含 Fable 5
4. 旧式凭证（`credential_scope` 为空）的席位行为与改动前逐字相同
5. 链路不完整的席位签发被守卫拒绝，且探测失败与 IP 不符给出可区分的报错
6. 落地节点保存 trojan/vmess 协议被拒；订阅导入的节点一律落为 `FRONT`
7. **吊销假设验证**（阻断上线）：对一个已签发凭证调用 §9.2 的吊销端点，随后用该凭证发起请求应返回 401。若未失效，改以 refresh_token 吊销再验一次；两者皆不生效则须修订 §9.2 与 §9.5，并在后台明确标示「提前退订无法即时失效」
8. 一个短周期订阅（如 7 天）签发后，凭证有效期与订阅同步
9. 改动订阅 `startsAt` 使 `endsAt` 前后移动，该席位均被标记为「凭证待更新」
10. 手工录入凭证后，五个元数据列被清空，该席位行为退回旧式（客户端不注入 scope 变量）
