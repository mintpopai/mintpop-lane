# 官网 SEO：关键词落位与指南内容页

> 日期：2026-09-12 · 状态：已确认，待实施

## 背景

官网（`apps/website`）的技术层 SEO 在 `2026-08-25-website-i18n-design.md` 落地时已经就位：vite-ssg 预渲染、canonical、hreflang 三连、sitemap、robots、`SoftwareApplication` 结构化数据、按语言的 OG 卡片、nginx 对未知路径返回真 404。

但它**搜不到**。核心目标用户是「想稳定使用 Claude / ChatGPT 的人」，而：

- `<title>` 是「MintPop Lane · 打开就能写代码」，H1 与 description 里没有一个这类用户会输入的搜索词（「稳定使用 Claude Code」「Claude 封号」「国内用 Claude」）。
- 全站只有首页一个 URL。「国内怎么用」「为什么被封」「和镜像站有什么区别」「ChatGPT / Codex 能不能稳定用」是四种不同的搜索意图，一页塞不下。
- FAQ 用了原生 `<details>`，爬虫读得到正文，但没有 `FAQPage` 结构化数据，拿不到富摘要。
- Google Search Console、Bing Webmaster、百度站长平台都还没提交。

## 目标

让在 Google（中英）、百度、必应上搜「稳定使用 Claude Code / ChatGPT」相关问题的用户能找到官网，并在落地页上得到诚实、有用的答案。

## 已确认的决策

1. **ChatGPT 流量承接但如实说明**：产品当前只接入 Claude Code。ChatGPT / Codex 相关页面与 FAQ 都明确写「Lane 当前接入的是 Claude Code，Codex 在规划中」，不做误导性承诺。
2. **目标搜索引擎**：Google（中文 + 英文）、百度、必应。三家都走 DNS 验证，代码不加验证 meta。
3. **范围**：首页关键词优化 + FAQ 结构化数据 + 4 篇中英指南页 + 站长平台运维文档。不引入 markdown 博客子系统（当前 4 篇内容用不上，YAGNI）。
4. **H1 允许微调**：Hero 标题第一句「想用真正的官方 Claude」改为「想稳定使用官方 Claude Code」，其余保持设计稿口吻。

## 非目标

- 桌面端、管理端不动。
- 不做第三种语言。
- 不引入 markdown / CMS，不做 RSS。
- 不做付费投放、外链建设（文档里只列建议）。
- 不改 nginx 的路由策略（nested 目录 + `try_files $uri $uri/ =404` 已能承载新路由）。

## 一、关键词地图

按搜索意图分组，每组落到一个 URL。首页押「产品词 + 核心痛点」，指南页各押一组长尾。

| 意图 | 中文搜索词（示例） | 英文搜索词（示例） | 落地页 |
|---|---|---|---|
| 产品 / 一站式 | 稳定使用 Claude Code、官方 Claude Code 订阅、Claude Code 一站式 | official Claude Code access, Claude Code subscription service | `/`、`/en/` |
| 国内怎么用 | 国内使用 Claude Code、Claude Code 国内怎么用、Claude 国内订阅支付 | use Claude Code from China | `/guides/claude-code-in-china/` |
| 封号原因 | Claude 账号被封、Claude 封号原因、Claude 账号异常 | Claude account banned, why Claude bans accounts | `/guides/why-claude-accounts-get-banned/` |
| 方案对比 | Claude 镜像站、Claude 中转 API、官方 Claude Code 区别 | Claude API relay vs official, Claude mirror site | `/guides/official-claude-code-vs-relay/` |
| ChatGPT / Codex | ChatGPT 稳定使用、ChatGPT 账号被封、Codex 国内 | stable ChatGPT access, Codex from China | `/guides/stable-chatgpt-codex/` |

英文页路径前缀 `/en/`，slug 不翻译（同一 slug 中英各一份，hreflang 互指）。

## 二、首页改动

### title / description（`copy.ts` 的 `meta`）

- zh title：`稳定使用官方 Claude Code：账号、订阅、专属出口一站搞定 | MintPop Lane`
- zh description：围绕「想稳定使用 Claude Code 的开发者」写，覆盖：官方账号与订阅代办、独立美国出口降低封号风险、内置终端免配置、支持 macOS（Apple 芯片）与 Windows，并带一句「ChatGPT Codex 接入规划中」。控制在 150 字以内。
- en title：`Stable, official Claude Code access — account, subscription and dedicated exit handled | MintPop Lane`
- en description：同上意图的英文重写，≤ 160 字符。

⚠️ `quality-website.yml` 的产物校验用英文标题片段 `Open it and start coding` 断言 locale 未串台。标题一改，该断言必须同步改成新标题里的片段，否则 CI 必红。

### Hero

- zh `hero.title[0]`：「想稳定使用官方 Claude Code，却不想折腾账号、支付、环境…… 🤯」
- en `hero.title[0]`：「Want stable, official Claude Code without wrangling accounts, payments and environments… 🤯」
- 其余 Hero 文案不动。

### FAQ

在现有 5 条后追加 3 条（中英各自撰写，结构一致）：

1. Claude 账号为什么会被封？Lane 怎么降低风险？——答：共享出口、出口地址频繁变化、支付信息异常是常见诱因；Lane 每个账号独立固定出口、自营 IP、账号与支付由我司代办；并附售后承诺。链到封号指南页。
2. 支持 ChatGPT / Codex 吗？——答：当前只接入 Claude Code；Codex 在规划中，上线后应用内自动出现。链到 ChatGPT / Codex 指南页。
3. 和自己订阅 Claude Pro 有什么区别？——答：额度按席位规格给（Standard 1.25 倍 PRO、Premium 6.25 倍 PRO），账号、支付、出口、终端都不用自己弄；用的仍是官方 Claude Code。

FAQ 答案里允许带一个页内链接，故 `faq.items[].a` 保持纯字符串，新增可选字段 `more?: { href: string; label: string }`，组件在答案下渲染成链接。

`FaqSection.vue` 追加 `useHead` 输出 `FAQPage` JSON-LD（`mainEntity` 逐条来自 `t.faq.items`，只用 `q` / `a` 纯文本）。

### 延伸阅读区块

首页 FAQ 之前新增 `GuidesSection.vue`：kicker「延伸阅读」/「Guides」，列出 4 篇指南的标题 + 一句摘要，链接到对应语言的指南页。数据来自指南注册表（见第三节），不在 `copy.ts` 重复维护标题。

### 页脚

`footer.links` 追加 4 条指南链接（同样来自注册表，`TheFooter.vue` 拼接渲染），作为全站内链。

## 三、指南页

### 内容模型（`src/content/guides/`）

每篇指南一个文件 `src/content/guides/<slug>.ts`，导出 `Record<Locale, GuideCopy>`；`src/content/guides/index.ts` 汇总成注册表：

```ts
export interface GuideSection {
  heading: string;           // h2
  paragraphs: string[];      // 段落，纯文本
  bullets?: string[];        // 可选列表
}

export interface GuideCopy {
  meta: { title: string; description: string };   // <title> 与 description，可与 h1 不同
  kicker: string;            // 面包屑下的小标签，如「指南」
  h1: string;
  intro: string;             // 引言段，加粗展示
  updated: string;           // ISO 日期 "2026-09-12"，进 Article.dateModified
  sections: GuideSection[];
  faq: { q: string; a: string }[];   // 页内 FAQ，也输出 FAQPage JSON-LD
  cta: { title: string; body: string; label: string };   // 页尾 CTA，链到首页下载区
}

export interface Guide {
  slug: string;
  copy: Record<Locale, GuideCopy>;
}

export const GUIDES: Guide[];   // 顺序即首页「延伸阅读」与页脚的展示顺序
```

内容取向沿用 `copy.ts` 头部的原则：说用户能感觉到的好处，不解释实现；每段先给结论。四篇的骨架：

1. **`claude-code-in-china`｜国内怎么稳定使用 Claude Code**：三道坎（支付、网络出口、账号风控）→ 自己折腾的常见做法与代价 → 镜像站 / 中转的取舍 → 用 Lane 的三步 → FAQ。
2. **`why-claude-accounts-get-banned`｜Claude 账号为什么会被封**：常见诱因逐条（共享出口、出口地址跳动、支付信息与地区不一致、非官方客户端）→ 自查清单 → Lane 怎么规避每一条 → 真被封了怎么办（售后承诺）。
3. **`official-claude-code-vs-relay`｜官方 Claude Code、镜像站与中转 API 的区别**：对比维度（模型与功能一致性、限流策略、隐私、稳定性、价格）→ 一张对比表（用 `bullets` 表达，不引表格组件）→ 什么情况选哪种。
4. **`stable-chatgpt-codex`｜想稳定使用 ChatGPT / Codex**：ChatGPT 用户遇到的同一类问题（支付、出口、封号）→ 原理相同 → **明确写明 Lane 当前接入 Claude Code、Codex 在规划中** → 现在能做什么（先用 Claude Code / 留联系方式）。

### 页面组件（`src/pages/GuidePage.vue`）

- 从 `route.params.slug` 在注册表里找到 `Guide`，按 `useI18n().locale` 取 `GuideCopy`。找不到的 slug 不会被预渲染，线上由 nginx 返 404，组件不做兜底页。
- 版式：`container` 内单列，`max-width: 760px`，面包屑（首页 › 指南标题）→ kicker → h1 → intro → 各 section（h2 + p + ul）→ 页内 FAQ（复用 `<details>` 样式）→ CTA 卡片。沿用 `styles.css` 的 kicker / section-title / btn 类，不新增设计 token。
- `useHead`：`title`、`description`、`og:title` / `og:description` / `og:image`（沿用首页两张卡片图）、`twitter:*`；JSON-LD 输出 `Article`（`headline` / `description` / `inLanguage` / `datePublished` = `dateModified` = `updated` / `author` + `publisher` = MintPop）、`BreadcrumbList`、`FAQPage`。

### 路由（`src/routes.ts` + `main.ts`）

新增纯模块 `src/routes.ts`，导出：

```ts
export const SITE = "https://lane.mintpop.ai";
export function guidePath(locale: Locale, slug: string): string;   // zh → /guides/<slug>/，en → /en/guides/<slug>/
export const ROUTE_PATHS: string[];   // 全部预渲染路径：/、/en/ 与每篇指南的两种语言
```

`main.ts` 的 `routes` 由 `ROUTE_PATHS` 派生（首页两条用 `HomePage`，其余用 `GuidePage`，`path` 形如 `/guides/:slug` 与 `/en/guides/:slug`），并把 `ROUTE_PATHS` 交给 vite-ssg 的 `includedRoutes` 让动态路由被逐条预渲染。`SITE` 常量从 `App.vue` 挪到这里，供 sitemap 测试与页面共用。

### 语言切换与 head 的路径感知

- `i18n.ts` 新增 `switchLocalePath(path: string, to: Locale): string`：把当前路径换成另一种语言的同一页（`/` ↔ `/en/`，`/guides/x/` ↔ `/en/guides/x/`）。`localePath(l)` 保留为「该语言首页」。
- `TheHeader.vue` 的切换按钮改用 `switchLocalePath(route.path, next)`。
- `App.vue` 的 canonical / hreflang / `og:url` 改为按 `route.path` 计算（中文版路径与英文版路径各一条 + x-default 指中文版）。`title` / `description` / `og:title` / `og:description` / `og:image` / `twitter:*` / `SoftwareApplication` JSON-LD 从 `App.vue` 挪到 `HomePage.vue`——每个页面各管自己的标题与描述，`App.vue` 只留与路径相关的通用项和 `htmlAttrs.lang`。
- 顶栏导航的锚点从 `#pricing` 改为 `localePath(locale) + "#pricing"`（在指南页也能回首页对应区块；在首页上 `/#pricing` 与 `#pricing` 行为一致）。页脚同理。

### sitemap

`public/sitemap.xml` 保持手写，新增 10 条 URL（4 篇 × 2 语言 + 首页 2 条）。新增测试 `src/routes.test.ts`：读取 `public/sitemap.xml`，断言其 `<loc>` 集合与 `ROUTE_PATHS` 拼出的绝对 URL 集合**完全相等**——漏加、多写、尾斜杠不一致都会被抓住。

## 四、站长平台与运维文档

新建 `docs/website-seo.md`（项目内文档），内容：

1. 关键词地图（第一节那张表）与每页押的词，供后续写文案时对照。
2. 三家平台接入步骤：Google Search Console（DNS TXT 验证 → 提交 sitemap）、Bing Webmaster（可从 GSC 一键导入）、百度站长平台（CNAME 验证 → 提交 sitemap → 开通普通收录的 API 推送）。
3. 上线后观察项：GSC 的「网页索引」是否 10 条全收录、hreflang 报错、核心网页指标；百度是否收录 `/guides/`。
4. 后续可做但本次不做：外链、Codex 上线后回填第 4 篇。

## 五、测试与验证

- **类型**：`GuideCopy` 强制中英字段齐全；`vue-tsc` 兜底。
- **单测**（vitest，node 环境）：
  - `copy.test.ts` 现有结构比对自动覆盖新增 FAQ 条目；
  - `guides.test.ts`：每篇指南 zh/en 结构一致（复用 `copy.test.ts` 的 `diffStructure`，抽到 `src/content/structure.ts`）；slug 唯一且只含 `[a-z0-9-]`；`updated` 是合法 ISO 日期；
  - `i18n.test.ts` 补 `switchLocalePath` 用例（首页、指南页、带 hash 不处理、尾斜杠保留）；
  - `routes.test.ts`：sitemap 与 `ROUTE_PATHS` 集合相等。
- **构建校验**：`mise run build-website` 后 `dist/guides/<slug>/index.html` 与 `dist/en/guides/<slug>/index.html` 共 8 份存在；CI 的产物校验脚本改用新标题片段，并追加「每份 HTML 恰好一个 `<title>`、英文指南页含 `lang="en"`」的循环检查。
- **视觉**：`mise run run-website` 后用 Claude in Chrome 打开首页（新 FAQ、延伸阅读区块）与一篇中英指南页，桌面与 400px 宽各看一次。
- **SEO 自检**：构建产物里 grep 每页的 canonical、hreflang 三连、`application/ld+json` 数量（首页 2 段：SoftwareApplication + FAQPage；指南页 3 段：Article + BreadcrumbList + FAQPage）。

## 六、风险与取舍

- **标题变长**：Google 桌面端约显示 30 个汉字，新中文标题会被截断到「稳定使用官方 Claude Code：账号、订阅、专属出口一站搞定」附近，关键词在前，可接受。
- **ChatGPT 页面的跳出率**：如实说明「规划中」会让部分访客离开，但比误导后差评好；页面里给出「先用 Claude Code」与联系入口两条去路。
- **内容页与产品文案重叠**：指南页会复述首页的卖点，这是有意的（每页都要能独立成立），但措辞不逐字复制，避免被判重复内容。
- **CI 断言耦合标题文案**：改标题就要改 CI，本次一并改；后续再改标题时以 CI 红为提醒。
