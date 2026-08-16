# Mintpop 受控终端 · 设计文档

- 日期：2026-08-16
- 状态：已评审，待实现
- 子项目：第一期（终端本体 + 内嵌链式代理 + 强制收口）

## 1. 背景与目标

公司为员工采购了**正版 Claude 团队账号**，员工用它在本地跑 Claude Code、Codex 等命令行 Agent。直接从国内网络访问 Anthropic 会触发风控甚至封号，而员工自行寻找的第三方反代、共享账号、镜像站又存在 **prompt 内容被窃取**的风险。

因此需要自研一款类 Warp 的桌面终端：**员工在这个终端里跑 Agent，流量被强制收口到公司可控的链式代理上**，从而同时达成两个目标：

1. **合法使用**——用正版账号 + 干净稳定的出口 IP，避免账号被风控。
2. **内容不外泄**——不经过任何不明第三方代理或反代，链路两跳都由公司掌握。

关键认知：员工绕过本终端直接从国内 IP 使用，**惩罚会自然落在员工自己头上（账号被封）**。因此本产品对员工是「保护装置」而非「监工」，产品设计取向应是**默认正确、易于使用**，而非对抗式管控。

## 2. 范围

### 本期做

- 桌面终端本体（PTY + 终端渲染，能完整跑 Agent 的 TUI）
- 内嵌 mihomo 内核，实现「机场节点 → 后置落地代理」两跳链式代理
- Agent 子进程的强制代理注入与 fail-closed 保护
- Claude 长效凭据由服务端下发并注入，**员工无需自行登录 Claude**
- Logto 统一身份接入
- 服务端薄实现：链路与凭据下发 + 心跳吊销 + 每人固定落地 IP 绑定

### 本期不做（后续子项目）

- 管控后台（员工管理、节点池运维、使用审计报表）
- 系统级网络强制（macOS NetworkExtension / Windows WFP），用于对抗性场景
- 内容侧审计与 DLP
- Warp 式的 block 命令历史、智能补全、内置 AI

## 3. 威胁模型与安全不变量

### 威胁模型：防绕，不防对抗

假设员工**可能主动绕过**（改环境变量、改配置文件、直接在 iTerm 里跑 Agent），但**不假设员工具备恶意内鬼能力**（不对抗 root 权限下的内存 dump、二进制篡改）。

| 能防住 | 防不住 |
|---|---|
| 员工看到或抄走节点地址与密码（不落盘、不进渲染层） | 有管理员权限的员工 attach 调试主进程、dump 内存捞凭据 |
| 手改配置文件换出口（配置只存在于 mihomo 内存） | 员工自行抓取 App 流量分析其行为 |
| 同机其它进程蹭本地代理端口（回环绑定 + 认证） | 员工从自己的 shell 里读到注入的 Claude 凭据并带走 |

关于最后一条：Claude 长效凭据以环境变量形式注入子进程，**员工在自己的 tab 里 `echo $CLAUDE_CODE_OAUTH_TOKEN` 即可看到**。这是「注入」这一形态的固有代价，不打算用混淆去掩盖。之所以可以接受：

- 员工本就有权使用该账号，凭据本身对他不是秘密；
- 带走它唯一的用途是脱离本终端使用，而那正是会触发 Anthropic 风控、把账号封掉的行为——惩罚自然落回员工自己头上；
- 服务端可随时轮换与吊销该员工的凭据。

若后续确需收紧，可改用 Claude Code 的 `apiKeyHelper` 机制（凭据不进环境变量，由外部程序按需吐出），但这只是提高门槛而非消除，列为后续加固项。

对「防不住」的部分，整体缓解手段是**凭据短期化 + 服务端可随时吊销 + 产品内强提示（绕过=封号）**，而不是在客户端堆混淆。

### 安全不变量（测试必须守住）

> **任何状态下都不存在「回落直连」这条分支。**

链路不可用时，宁可让员工无法工作，也绝不让流量裸奔出去。这条不变量的具体落地见第 8 节。

## 4. 架构总览

```
┌─ Tauri 主进程 (Rust) ───────────────────────────────┐
│  · 身份：Logto OIDC 登录，refresh_token 存 OS 钥匙串 │
│  · 链路：向服务端拉链路配置，仅存内存                │
│  · mihomo 生命周期：生成配置、拉起、健康检查、销毁   │
│  · PTY 管理：每个 tab 一个子进程 + 环境注入          │
│  · 渲染层只拿到「链路状态」，永远拿不到凭据          │
└──────────┬───────────────────────┬──────────────────┘
           │ 生成配置/健康检查      │ spawn + env 注入
   ┌───────▼────────┐      ┌───────▼───────────────────┐
   │ mihomo sidecar │◄─────┤ agent 子进程 (claude/codex)│
   │ 127.0.0.1:随机 │ 代理  │ HTTPS_PROXY=http://u:p@…  │
   │ mixed + 认证   │      └───────────────────────────┘
   └───────┬────────┘
           │  listeners[].proxy 钉死出口，绕过全局规则
           ▼
   FRONT（美国机房机场节点）──dialer-proxy──► LAND（后置落地代理）──► api.anthropic.com
```

### 刻意为之的边界

- **mihomo 只服务本 App**：绑 `127.0.0.1`、端口随机、开启 `users` 认证（一次一密）。同机其它进程即使扫到端口也用不了。
- **出口靠 `listeners[].proxy` 钉死，不靠 `rules` 分流**：规则可被绕过，钉死的入站出口不可。
- **凭据永不进渲染层**：前端只显示链路状态，节点地址与密码不下发到 WebView，避免 DevTools 一开即抄走。
- **注入发生在 Rust 侧 spawn 的那一刻**：JS 层无法篡改，且没有绕过该路径的命令。

## 5. 网络链路与 mihomo 配置

### 5.1 启动时序（凭据全程不落盘）

1. 主进程生成**引导配置**（权限 0600，App 私有目录）：只含随机 `external-controller` 端口与随机 `secret`，**不含任何节点信息**，规则兜底 `MATCH,REJECT`。
2. 拉起 mihomo sidecar。此刻它是一个「什么都连不上」的空壳。
3. 员工登录 Logto → 服务端下发链路 → 主进程在内存中拼出完整 YAML → 通过 `PUT /configs?force=true` 以 `payload` 字段热加载。

节点地址与密码只存在于**主进程内存**和**这一次本地 HTTP 请求体**中，不写入任何文件。

> `secret` 走引导配置文件而非命令行参数：命令行在 `ps` 中对同机其它用户可见。

### 5.2 配置骨架

```yaml
allow-lan: false
mode: rule
find-process-mode: off

proxies:
  - name: FRONT              # 第一跳：美国机房机场节点，负责出国
    type: <vmess|trojan|ss>
    server: <服务端下发>
    # ...协议相关字段
  - name: LAND               # 第二跳：后置落地代理，决定最终出口 IP
    type: socks5
    server: <服务端下发>
    dialer-proxy: FRONT      # 链式：先连 FRONT，再从 FRONT 连 LAND

listeners:
  - name: agent-in
    type: mixed
    listen: 127.0.0.1        # 只监听回环
    port: <随机高位端口>
    proxy: LAND              # 出口钉死，完全绕开 rules
    users:
      - username: <一次一密>
        password: <一次一密>

rules:
  - MATCH,REJECT             # 安全默认：任何落到规则的流量一律拒绝
```

### 5.3 fail-closed 的两道闸

1. **mihomo 侧：`MATCH,REJECT`**。listener 的 `proxy` 钉死后流量本不经过规则；一旦配置推送失败或 listener 失效，流量会落到规则上——此时必须是「断网」而非「直连」。
2. **App 侧：出口 IP 校验**。启动 Agent 前，主进程经该 listener 发一次探测请求，将返回的出口 IP 与服务端下发的 `expected_egress_ips` 比对。不匹配（链路断开、或只通了第一跳）则拒绝启动 Agent。这同时验证了两跳都真实走通。

### 5.4 端口与凭据生成

- **端口**：在高位区间（如 20000–60000）随机取值，先试绑确认空闲再写入配置；被占用则重试。
- **凭据**：32 字节 CSPRNG 随机数经 Base64 编码，App 每次启动重新生成，不持久化。

## 6. Claude 凭据下发

**员工不自行登录 Claude。** 若走 `claude /login`，会拉起**系统浏览器**完成 claude.ai 授权，而系统浏览器走员工自己的网络——那会在 Anthropic 侧留下一个国内登录 IP，恰恰是最容易触发风控的一步，且终端注入的环境变量管不到系统浏览器。

**做法**：管理员在受控环境中用 `claude setup-token` 为每个团队席位预先生成**长效凭据**，录入服务端；员工登录 Logto 后，凭据随链路配置一并下发，由主进程注入 Agent 子进程的环境变量 `CLAUDE_CODE_OAUTH_TOKEN`。

由此得到的性质：

- 员工机上**从不发生 Claude 侧的登录动作**，Anthropic 看到的所有请求都来自该员工的固定落地 IP，画像干净且稳定。
- 员工无需理解链路与账号的关系，打开终端即可用——符合「默认正确、易于使用」的产品取向。
- 凭据与员工是**一对一绑定**（一席位一凭据），出问题可精确定位与单独吊销。

凭据同样只存在于主进程内存与注入给子进程的环境变量中，不落盘。其对员工 shell 可见的固有代价与取舍见第 3 节。

## 7. 服务端契约

服务端第一期只做三件事：**验证员工身份 → 下发链路 → 可吊销**。它自己不承载任何代理流量。

### 7.1 身份

认证完全交给 **Logto**，不自建账号体系。

| 环节 | 做法 |
|---|---|
| 登录 | 系统浏览器打开 Logto 授权页 → Authorization Code + PKCE → 自定义 scheme 回调 `mintpop://callback` 回到 App |
| 令牌存储 | `refresh_token` 存入 OS 钥匙串（macOS Keychain / Windows 凭据管理器），不落普通文件 |
| 调用链路服务 | 携带 Logto 签发的 `access_token`（audience 为链路服务的 API Resource），服务端用 Logto JWKS 验签 |
| 吊销 | 员工在 Logto 停用 → refresh 失败 → 客户端断链并 fail-closed |

> 桌面端属于 public client，无法安全保存 client secret，因此必须使用 PKCE。

### 7.2 接口

统一返回 `ApiResponse<T>`（`code` 为 0 表示成功，非 0 为业务错误码；`data` 为业务数据；`msg` 为可读信息）。HTTP 状态码一律 200，业务结果只看 `code`。

| 接口 | 作用 | `data` 要点 |
|---|---|---|
| `GET /api/link/config` | 拉取链路配置与凭据 | `front`（第一跳节点完整配置）、`land`（后置落地代理配置）、`expected_egress_ips[]`、`claude_credential`（该员工席位的长效凭据）、`ttl_seconds` |
| `POST /api/link/heartbeat` | 续期与吊销检查 | `status`：`ACTIVE` / `REVOKED` / `SUSPENDED` |

### 7.3 出口 IP 分配

**每名员工绑定一个固定落地 IP**。服务端维护 IP 池与员工的长期绑定关系，`GET /api/link/config` 按调用者身份返回其专属 `land` 节点与 `expected_egress_ips`。

理由：Anthropic 风控对「同一账号 IP 频繁跳变」与「多个账号挤在同一 IP」都敏感。一人一 IP 是风控画像最干净的形态，代价是需要采购多个出口并做分配管理。

落地代理更换 IP 时，服务端更新绑定，客户端在下次心跳后自动跟上。

**Claude 席位凭据同样一人一份**，与落地 IP 绑定在同一条员工记录上：`员工 → (落地出口, Claude 席位凭据)`。这样「谁在用哪个 IP、用哪个席位」始终一一对应，风控排查与吊销都能精确到人。

### 7.4 错误码分段

按 6 位分段编码：

| 号段 | 用途 | 示例 |
|---|---|---|
| `11xxxx` | 通用/系统 | `110001` 参数非法、`110002` 服务内部错误 |
| `21xxxx` | 认证与身份 | `210001` 令牌无效、`210002` 令牌过期、`210003` 账号已停用 |
| `31xxxx` | 链路 | `310001` 无可用出口、`310002` 未分配落地 IP、`310003` 链路已吊销 |

客户端 Rust 侧镜像一份同名枚举，取值逐字一致。

## 8. 状态机与失败行为

链路状态枚举（成员名与字符串取值逐字一致，两端镜像）：

`DISCONNECTED` / `CONNECTING` / `ACTIVE` / `DEGRADED` / `REVOKED`

| 事件 | 行为 |
|---|---|
| 未登录 Logto | 禁止开 tab |
| 链路配置拉取失败（重试 3 次） | `DISCONNECTED`，禁止开 tab |
| 出口 IP 校验不匹配（含只通第一跳） | `DEGRADED`，禁止开新 tab，已有 tab 顶部横幅告警 |
| 心跳返回 `REVOKED` | mihomo 配置重置为空壳，网络当场中断，**不主动 kill 员工进程** |
| mihomo 进程崩溃 | 自动重启并重推配置；连续 3 次失败则 `DISCONNECTED` |
| 员工机断网 | `DISCONNECTED`，禁止开 tab |

心跳返回 `REVOKED` 时不 kill 员工进程，是为了避免丢失正在进行的工作——让 Agent 自己报网络错误即可。

## 9. 终端本体

- **渲染**：Tauri 2 + `xterm.js`（配 WebGL/canvas addon）。这是跑 TUI 最稳的选择，能完整支持 Agent 的全屏 TUI、真彩色、鼠标事件与 `SIGWINCH` 改窗口大小。
- **PTY**：Rust 侧使用 `portable-pty`（Windows 走 ConPTY）。每个 tab 对应一个 PTY 与一个子进程，前端只做「字节流进、字节流出」，不含任何逻辑。
- **唯一注入入口**：所有子进程都必须经过 Rust 侧的 `spawn_agent_pty()`：

  1. 链路状态不为 `ACTIVE` → 拒绝，提示「公司链路不可用」
  2. 出口 IP 探测不通过 → 拒绝
  3. 构造子进程环境变量：
     - `HTTPS_PROXY` / `HTTP_PROXY` / `ALL_PROXY` = `http://<u>:<p>@127.0.0.1:<随机端口>`
     - `NO_PROXY` = `localhost,127.0.0.1,::1`
     - `CLAUDE_CODE_OAUTH_TOKEN` = 服务端下发的该员工席位长效凭据
  4. 创建 PTY 并 spawn

- **shell 范围**：tab 中启动的是环境已注入的登录 shell，员工可在其中执行任意命令。不做命令白名单——Agent 本身就能执行任意命令，限制 shell 没有额外安全收益。

## 10. 测试策略

遵循先写测试的开发习惯。

- **单元测试**
  - 链路配置 → mihomo YAML 的生成（快照测试）
  - 状态机转移表全覆盖
- **集成测试**
  - 启动真实 mihomo + 两个本地假节点模拟 FRONT / LAND，验证流量确实经过两跳
  - 验证 `MATCH,REJECT` 生效（未经 listener 的流量被拒绝）
  - 验证 listener 认证生效（无凭据连接被拒）
- **泄漏测试（最关键）**
  - 断链后确认子进程**真的连不出去**，而非悄悄直连。这条测试失败即意味着产品失去意义。
- **人工冒烟**
  - 真实 `claude` 跑通一轮完整对话，验证 TUI 交互（改窗口大小、粘贴、Ctrl-C）

## 11. 技术栈与仓库形态

- **Monorepo**：`apps/desktop`（Tauri 2 + Vue 3 + xterm.js）、`apps/server`（Go 薄服务）
- **CI/CD 命名**：tag 前缀 `desktop-v*` / `server-v*`；workflow `release-desktop.yml`、`deploy-server.yml`、`ci-repo.yml`，以及必备的 `action-notify.yml`
- **工具链**：全部收口到根 `mise.toml`，钉死 rust / node / pnpm / go 的具体版本；task 按「动作-组件」命名（`run-desktop`、`build-desktop`、`test-server` 等）
- **mihomo**：作为 Tauri sidecar 打包进安装包，版本随 App 固定
- **目标平台**：macOS arm64（13 及以上）+ Windows x64

## 12. 待验证风险清单

实现前需要逐条验证，**第 1 条是整个方案的地基，应在写任何 UI 之前先行验证**。

| # | 风险 | 验证方式 | 退路 |
|---|---|---|---|
| 1 | `HTTPS_PROXY` 是否被 Claude Code 无条件遵循。已确认 2.x 是原生二进制且内部含 `HTTPS_PROXY` 处理，但也出现了 `NODE_USE_ENV_PROXY`，说明存在开关语义 | 起 mihomo + 注入环境变量 + 跑一次 `claude`，从 mihomo 日志确认连接确实经过 listener | 补设 `NODE_USE_ENV_PROXY=1`；仍不行则改用 `ANTHROPIC_BASE_URL` 指向本地反代 |
| 2 | `CLAUDE_CODE_OAUTH_TOKEN` 注入后能否完全跳过交互式登录 | 在干净的 `HOME` 下只给该变量跑一次 `claude -p`，确认不弹登录 | 改用 `apiKeyHelper` 机制提供凭据 |
| 3 | mihomo `PUT /configs` 携带 `payload` 时是否会将配置写回磁盘 | 推送后检查磁盘文件内容 | 改为写 0600 临时文件并在加载后立即删除 |
| 4 | `claude setup-token` 生成的长效凭据的有效期与轮换周期 | 生成后记录其过期时间 | 服务端按周期批量轮换并下发 |

## 13. 后续子项目

1. **管控后台**：员工与节点池管理、IP 分配运维、使用审计报表
2. **系统级网络强制**：macOS NetworkExtension / Windows WFP + MDM 推送，用于需要对抗性防护的场景。本期架构无需推倒即可叠加
3. **终端体验增强**：按实际使用反馈决定是否引入 block 式历史、补全等
