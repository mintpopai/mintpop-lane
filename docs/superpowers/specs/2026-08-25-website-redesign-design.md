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

> 注意这张表是**问题诊断**，不是内容清单。落地页要传达的是这些机制换来的**好处**（不用自己配网络、不用填密钥、出问题知道找谁），机制本身不搬上页面——见下方「文案原则」。

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
- **深色终端井全站只出现一次**（「内置终端」那节），保证它是视觉重音而非装饰。

### 品牌资源

从 `mintpop-standards/docs/public/assets/brand/` 复制到 `apps/website/public/brand/`：

- `icon/mintpop-icon-256.png`（Pop Mark）
- `wordmark/mintpop-wordmark-dark.png`（浅底用）、`wordmark/mintpop-wordmark-light.png`（深底用）
- `favicon/` 全套（16/32/48/180/192/256 + apple-touch-icon）

页头改为 **官方词标图 + 分隔线 + 「Lane」文字**：品牌名部分一律用官方图，产品名 `Lane` 才是文字。
（先试过用 Pop Mark 小图标，28px 下爆裂短线糊成一团、读不出是什么，换成横版词标。）
删除自造的 `public/favicon.svg`。

## 二、信息架构

| # | 区块 | 底色 | 内容 |
|---|---|---|---|
| 1 | Header | 玻璃白 | 词标 + Lane｜链路 · 终端 · 常见问题｜下载按钮，sticky |
| 2 | Hero | 白 + Mint 光晕 | 主标 + 副标 + 双 CTA + 版本药丸；下方产品窗口示意 |
| 3 | 专属链路 | Cloud | 三节点图讲解 + 三条要点 |
| 4 | 状态可诊断 | 白 | 一对左右对照 |
| 5 | 内置终端 | **深色井** | 终端 mock + 四条要点 |
| 6 | 三步开始 | 白 | 装上 → 登录 → 开写 |
| 7 | 下载 | Cloud | 复用 `useRelease`；加系统要求，明说不支持 Intel Mac |
| 8 | 常见问题 | 白 | 5 条 |
| 9 | Footer | 深色 | 白字词标 + 链接 + 版权 |

### 关键区块的内容契约

**区块 4「状态可诊断」** 用一对左右对照讲清「出错时说人话」这个差异点：

| | 页面上的内容 |
|---|---|
| 左 · 常见的做法 | 一行 `Error: connection failed`，其余留白 |
| 右 · Lane | 「已暂停」徽章 + 出口对不上的具体原因 + 「重新连接」按钮 + 一句该谁动手 |

两张卡等高，**左边那片留白就是论据本身**，不要拿内容把它填满。

叙事重点是「**出了问题它会说清卡在哪一步**」——不是甩一句「连接失败」让用户自己查半天，
而是分清「该等一下」和「该你动手」。这一点在产品里是真的（桌面端 `src/link.ts` 的四种处境
各有各的文案与重试入口），官网只举其中一种（`EGRESS_MISMATCH`）当代表。

> ⚠️ **只举一种处境，且必须选「不是你的错」那类。** 早先的版本平铺四张状态卡
> （`NOT_PROVISIONED` / `EGRESS_UNREACHABLE` / `EGRESS_MISMATCH` / `ACTIVE`），四张里三张
> 是故障，落地页第三屏就在演示产品的三种失败方式，与首屏「打开就能写代码，别的都替你办好了」
> 自相矛盾；其中「还没给你分配链路，管理员配好之后」暴露装完可能用不上，「链路连不上，
> 多半是你这边的网络」出错先甩锅用户——都不适合出现在宣传页。换处境时守住这条：举的例子
> 要能体现产品在替用户挡事，而不是罗列用户会遇到的麻烦。
>
> ⚠️ 页面上的说法是桌面端处境的**白话转述，不是逐字镜像**（逐字搬过来会带上「配置已就位」
> 「出口校验不通过」这类术语，落地页读起来像故障手册）。语义要跟桌面端保持一致，桌面端调整
> 处境划分时官网要跟着改；但用词不必逐字相同。
>
> ✅ **术语已统一（2026-08-25）**：官网、桌面端 UI、服务端、管理端一律说「链路」。此前官网
> 为了更口语单独说「线路」，现已统一——「链路」是产品其余部分的事实主术语（server 50 处、
> admin 4 处、本目录 228 处），本身也足够正式。官网不再保留独立术语。

**区块 8** 的下载逻辑一字不动地复用现有 `composables/release.ts` + `useRelease.ts`——清单契约（`downloads.json` 的 `version` / `platforms.<平台键>.url|size`）与桌面端发布侧绑定，不在本次重做的范围内。

### 文案原则

**说用户能感觉到的好处，不解释我们怎么实现的。** 出口探测、凭据注入顺序、sha256、验签这些机制
留在代码和 CLAUDE.md 里，落地页只回答一件事：用了它，你能少折腾什么。

自查标准：每句话都问一遍「读者看完会不会想『那跟我有什么关系』」，会就删掉重写。

同时延续桌面端 `link.ts` 那种「不撒谎」的措辞，不承诺产品做不到的事：

- ✅「账号被别人牵连的可能性从一开始就小得多」
- ❌「永不封号」「军工级加密」「100% 安全」

## 三、技术落地

### 文件变更

**保留不动**：`composables/release.ts`、`composables/useRelease.ts`、`composables/release.test.ts`、`nginx.conf`、`Dockerfile`、`vite.config.ts`。

**重写**：`src/styles.css`（tokens + 工具类）、`src/App.vue`、全部 `src/sections/*.vue`、`index.html`。

**新增**：

- `src/content/copy.ts` — 文案集中一处（对齐 keeper-website 的做法）
- `src/components/LaneVisual.vue` — Hero 的产品窗口示意
- `src/components/LanePathGraphic.vue` — 三节点链路图（可复用于区块 3）
- `src/components/TerminalMock.vue` — 深色终端块
- `src/components/SituationCard.vue` — 状态卡
- `src/sections/VerifySection.vue`、`FaqSection.vue`
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
