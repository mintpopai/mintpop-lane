# 官网 SEO 运维手册

> 站点：https://lane.mintpop.ai（Cloudflare 代理）。设计见 `docs/superpowers/specs/2026-09-12-website-seo-design.md`。

## 关键词地图（每页押一组意图，写文案时对照）

| 落地页 | 中文意图 | 英文意图 |
|---|---|---|
| `/`、`/en/` | 稳定使用 Claude Code、官方 Claude Code 订阅、Claude Code 一站式 | official Claude Code access, Claude Code subscription service |
| `/guides/claude-code-in-china/` | 国内使用 Claude Code、Claude Code 国内怎么用、Claude 国内订阅支付 | use Claude Code from China |
| `/guides/why-claude-accounts-get-banned/` | Claude 账号被封、Claude 封号原因、Claude 账号异常 | Claude account banned, why Claude bans accounts |
| `/guides/official-claude-code-vs-relay/` | Claude 镜像站、Claude 中转 API、官方 Claude Code 区别 | Claude relay vs official, Claude mirror site |
| `/guides/stable-chatgpt-codex/` | ChatGPT 稳定使用、ChatGPT 账号被封、Codex 国内 | stable ChatGPT access, Codex from China |

英文页在 `/en/` 前缀下同 slug。新增指南：`apps/website/src/content/guides/` 建文件 + `index.ts` 注册 + `public/sitemap.xml` 加两条（`routes.test.ts` 会拦住漏加）。

## 站长平台接入（一次性，三家都走 DNS 验证，不改代码）

1. **Google Search Console**：添加「网域」资源 `lane.mintpop.ai` → 按提示在 Cloudflare DNS 加 TXT 记录 → 验证通过后在「站点地图」提交 `https://lane.mintpop.ai/sitemap.xml`。
2. **Bing Webmaster Tools**：选「从 Google Search Console 导入」一键同步站点与 sitemap；或手动加站点后走 CNAME / DNS 验证。
3. **百度站长平台**（ziyuan.baidu.com）：添加网站 → 选 CNAME 验证（Cloudflare 加一条 CNAME，代理状态设为「仅 DNS」）→ 「普通收录」里提交 sitemap，并开通 API 推送（发新指南时用 curl 把 URL 推一次，比等抓取快）。

## 上线后观察（前两周每周看一次，之后每月）

- GSC「网页」报告：10 条 URL 是否全部「已编入索引」；「HTTP 状态」与「重复网页」里不该出现指南页。
- GSC「效果」：按查询过滤含 `claude` 的词，看展示与点击；关键词地图里的词若三周后仍零展示，回头改该页的 title / h1。
- GSC「国际定位」：hreflang 不该有错误（中英互指 + x-default）。
- 百度：`site:lane.mintpop.ai` 是否收录到 `/guides/`；百度对 Cloudflare 站点抓取偏慢，属正常。
- Bing：Webmaster 里「URL 检查」抽查一篇英文指南。

## 后续可做（本次未做）

- 外链：在 MintPop 主站（mintpop.ai）与桌面端 README 链回官网指南页。
- Codex 上线后回填 `stable-chatgpt-codex` 两种语言，把「规划中」改成实际说明并更新 `updated`。
- 若指南增长到十篇以上，再评估 markdown 内容子系统。
