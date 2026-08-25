# 官网重新设计（apps/website）

> 日期：2026-08-25 · 状态：已批准，进入实施

## 背景与问题

官网 `apps/website` 目前是 4 个纯文字区块（Hero / 特性 / 三步 / 下载）的单页站，存在三类问题：

**一、违反品牌 INVARIANT**

- 正文字体用 Inter，品牌规范固定为 **Space Grotesk**（不可改）。
- 页头**用文字排出 logo**（`MintPop Lane` 纯文本 + 自造 SVG 图标），品牌规范明确禁止「用文字排 logo」，并要求引用官方词标图。
- favicon 是自造的 "M" 折线图形，不是品牌 **Pop Mark**。

**二、产品最有说服力的机制一条都没讲**

桌面端真正的差异化在这些地方，官网只字未提：

| 机制 | 桌面端实现位置 |
|---|---|
| 出口校验：连上了还要验实测出口 == 专属出口，不符即暂停放行 | `link/probe.rs`、`LinkSituation::EGRESS_MISMATCH` |
| 13 种链路处境各自独立，网络问题绝不报成安全事件 | `src/link.ts` 的 `present` 表 |
| 安全不变量：只有 `ACTIVE` 才允许 spawn 会话 | `link/state.rs::allows_spawn()` |
| 凭据边界：注入席位凭据后清除能改变请求去向的变量 | `pty/agent.rs` 的 `conflicting_env` + `pty/session.rs::apply_env` |
| 自动装 agent CLI（约 325MB，走受控链路 + sha256 校验） | `install/` |
| 多 tab 终端保活、UTF-8 增量解码、TERM 注入、unicode11 | `pty/utf8.rs`、`pty/env.rs`、`Terminal.vue` |
| 更新包 minisign 验签、R2 分发 | `updater.ts` + 发布流水线 |

**三、无产品视觉、节奏扁平、文案过时**

- 全站没有一张「应用长什么样」的图，这是「简陋」的根因。
- 每节都是 88px padding + 灰底描边卡片，没有层次与焦点。
- `StepsSection` 仍写「安装包直接从 GitHub Releases 获取」——制品早已迁到 Cloudflare R2。
- 缺 OG / Twitter card / JSON-LD / robots.txt / sitemap.xml；页脚只有一行版权。

## 目标

把官网从「一张说明书」重做成「一个能自己讲清楚产品内核的落地页」，同时修掉全部品牌违规。

**非目标（本次明确不做）**：英文版与预渲染（keeper-website 那套 vue-router + vite-ssg 不引入）、`nginx.conf` 改动、桌面端任何改动。

## 决策

四项方向已确认：

1. **视觉方向**：浅色控制台 + 深色终端井。
2. **受众**：个人开发者为主，团队为辅。
3. **范围**：设计 + 内容 + SEO 基础，仍是单页纯静态。
4. **产品视觉**：用 CSS/SVG 手写高保真示意，不用截图。

## 一、视觉系统

### Tokens

| 语义 | 值 | 说明 |
|---|---|---|
| `--bg` / `--bg-soft` / `--bg-mint` | `#FFFFFF` / `#F4F8F6` / `#EDFAF5` | 品牌 Cloud 分层 |
| `--ink` / `--ink-2` / `--ink-3` | `#0B0B0C` / `#4B5563` / `#6B7280` | 文本三级 |
| `--mint` / `--mint-bright` / `--mint-deep` | `#17D1A7` / `#1FE3AD` / `#0FB389` | 品牌 INVARIANT，不可改 |
| `--brand-text` / `--brand-strong` | `#0A8265` / `#087257` | **沿用现状**：白底上 Mint 只有 2.7:1，文字与实心按钮必须用压深档（4.8:1） |
| `--well` / `--well-ink` | `#101614` / `#D7E0DC` | **复用桌面端 `styles/tokens.css` 同名 token**，官网与产品共用同一块深色 |
| `--line` | `#E5E7EB` | 分隔线 |

单主题锁定浅色，不做暗色分支（沿用现状决策：品牌系是「白底 + 薄荷绿」）。

### 字体

| 角色 | 字体 | 变更 |
|---|---|---|
| 展示 / 标题 | **Fredoka** | 保留。品牌二选一，且桌面端 `--font-brand` 就是它 |
| 正文 / UI | **Space Grotesk** | **新增，替换 Inter**（修品牌违规） |
| 等宽 | **JetBrains Mono** | **替换 IBM Plex Mono**，与桌面端终端同一种字 |

全部走 Fontsource 自托管，禁止外链 Google Fonts；中文挂在西文之后走系统回退（`PingFang SC` / `Hiragino Sans GB` / `Microsoft YaHei` / `Noto Sans CJK SC`）。

### 节奏与层次

打破现有「每节都 88px」的平铺：

- Hero 特大留白；主章节 `112px`；次章节 `80px`。
- 底色交替：白 → Cloud → 白 → **深色井** → Cloud …
- **深色终端井全站只出现一次**（「打开就能写」那节），保证它是视觉重音而非装饰。

### 品牌资源

从 `mintpop-standards/docs/public/assets/brand/` 复制到 `apps/website/public/brand/`：

- `icon/mintpop-icon-256.png`（Pop Mark）
- `wordmark/mintpop-wordmark-dark.png`（浅底用）、`wordmark/mintpop-wordmark-light.png`（深底用）
- `favicon/` 全套（16/32/48/180/192/256 + apple-touch-icon）

页头改为 **Pop Mark 图 + 「Lane」文字**：品牌名部分用官方图，产品名 `Lane` 才是文字。删除自造的 `public/favicon.svg`。

## 二、信息架构

| # | 区块 | 底色 | 内容 |
|---|---|---|---|
| 1 | Header | 玻璃白 | Pop Mark + Lane｜链路 · 终端 · 安全 · 常见问题｜下载按钮，sticky |
| 2 | Hero | 白 + Mint 光晕 | 主标 + 副标 + 双 CTA + 版本药丸；下方产品窗口示意 |
| 3 | 一人一路 | Cloud | 三节点图讲解 + 三条要点 |
| 4 | 连上了还要验 | 白 | 出口校验叙事 + 四张真实处境卡 |
| 5 | 打开就能写 | **深色井** | 终端 mock + 会话/自动装 CLI/凭据注入/快捷键 |
| 6 | 凭据的边界 | Cloud | 四条安全约束 |
| 7 | 三步开始 | 白 | 下载 → 登录 → 开始编码 |
| 8 | 下载 | Cloud | 复用 `useRelease`；加系统要求，明说不支持 Intel Mac |
| 9 | 常见问题 | 白 | 6 条 |
| 10 | Footer | 深色 | 白字词标 + 链接 + 版权 |

### 关键区块的内容契约

**区块 4「连上了还要验」** 是本次重做的核心新增。它从桌面端 `src/link.ts` 的 `present` 表里取 4 种处境，**照搬真实的 `label` / `detail` / `tone` / 重试按钮措辞**：

| 处境 | label | tone | 重试措辞 |
|---|---|---|---|
| `NOT_PROVISIONED` | 尚未配置专属链路 | muted | 重新检查 |
| `EGRESS_UNREACHABLE` | 链路不通 | danger | 重新连接 |
| `EGRESS_MISMATCH` | 出口校验不通过 | warn | 重新连接 |
| `ACTIVE` | 已接入专属链路 | ok | 无 |

叙事重点是「**这四种情况用户能做的事完全不同，所以我们绝不把它们混成一句话**」——`EGRESS_UNREACHABLE`（你的网络问题）与 `EGRESS_MISMATCH`（安全事件）曾被混成同一句「出口不符」，把网络问题报成了安全事件，这是产品里被显式修掉的坑。

> ⚠️ 官网这四张卡的文案是桌面端 `link.ts` 的**镜像副本**，桌面端改文案时官网要同步。

**区块 8** 的下载逻辑一字不动地复用现有 `composables/release.ts` + `useRelease.ts`——清单契约（`downloads.json` 的 `version` / `platforms.<平台键>.url|size`）与桌面端发布侧绑定，不在本次重做的范围内。

### 文案原则

延续桌面端 `link.ts` 那种「不撒谎」的措辞：

- ✅「从源头降低共享代理带来的账号风控风险」
- ❌「永不封号」「军工级加密」「100% 安全」

不承诺产品做不到的事；处境类文案一律照实说明「现在是什么情况、你能做什么」。

## 三、技术落地

### 文件变更

**保留不动**：`composables/release.ts`、`composables/useRelease.ts`、`composables/release.test.ts`、`nginx.conf`、`Dockerfile`、`vite.config.ts`。

**重写**：`src/styles.css`（tokens + 工具类）、`src/App.vue`、全部 `src/sections/*.vue`、`index.html`。

**新增**：

- `src/content/copy.ts` — 文案集中一处（对齐 keeper-website 的做法）
- `src/components/LaneVisual.vue` — Hero 的产品窗口示意
- `src/components/LanePathGraphic.vue` — 三节点链路图（可复用于区块 3）
- `src/components/TerminalMock.vue` — 深色终端块
- `src/components/SituationCard.vue` — 处境卡
- `src/sections/VerifySection.vue`、`SecuritySection.vue`、`FaqSection.vue`
- `public/brand/**`、`public/robots.txt`、`public/sitemap.xml`、`public/og.png`

**删除**：`public/favicon.svg`（自造图形，违反品牌规范）。

### 依赖

```
+ @fontsource/space-grotesk  5.3.0
+ @fontsource/jetbrains-mono 5.3.0
- @fontsource/inter
- @fontsource/ibm-plex-mono
  @fontsource/fredoka        5.3.0  （保留）
```

### SEO

`index.html` 内补齐（单页静态站，写在 HTML 里即可，不引入 unhead）：

- `og:title` / `og:description` / `og:image` / `og:url` / `og:type` / `og:locale`
- `twitter:card=summary_large_image`
- JSON-LD `SoftwareApplication`（名称、平台、下载页、开发者）
- `<link rel="canonical">` 指向 `https://lane.mintpop.ai/`

`public/og.png` 为 1200×630，用品牌 PNG 资源合成（工具按环境择一：`sips` / `rsvg-convert` / headless Chrome）。

`robots.txt` 放行全站并声明 sitemap；`sitemap.xml` 只有一条 URL。

### 动效与可访问性

- 动效只动 `opacity` / `transform`；整段包在 `@media (prefers-reduced-motion: no-preference)` 里。
- 正文与背景对比度 ≥ 4.5:1；深色井内的绿改用 `--mint-bright` `#1FE3AD`（深底上够亮）。
- 状态一律「颜色 + 文字标签」双通道，不靠颜色单独传达（处境卡尤其）。
- 所有可聚焦元素给可见 focus 环。

### 全球可达

字体全部 Fontsource 自托管，全站**零第三方外链**（无 Google Fonts、无 CDN、无统计脚本），符合 `global-reachability.md`。构建产物里不应出现任何跨域引用。

### 验证

- `mise run lint-website`（eslint + vue-tsc）
- `mise run test-website`（vitest，现有 `release.test.ts` 必须继续通过）
- `mise run build-website`（构建通过）
- 人工核对：构建产物无跨域引用；深色井内文字对比度；`prefers-reduced-motion` 下无动画。
