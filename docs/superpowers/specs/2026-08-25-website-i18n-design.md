# 官网中英双语（apps/website i18n）

> 日期：2026-08-25 · 状态：待实施

## 背景

`2026-08-25-website-redesign-design.md` 把「英文版与预渲染（keeper-website 那套 vue-router + vite-ssg 不引入）」明确列为**非目标**——当时是有意推迟，本次补上。

现状（重做完成后的 `apps/website`）：

- **无路由的单页纯静态站**：无 vue-router、无 SSG，`main.ts` 直接 `createApp(App).mount()`；nginx 明确 `try_files $uri $uri/ =404`，不回退 index.html。
- 文案**已集中在 `src/content/copy.ts`**（165 行），这是本次最大的顺风——绝大部分句子不用从模板里刨。
- 但仍有一批中文散在模板与逻辑里：`TheHeader` / `HeroSection` / `DownloadSection` / `TheFooter` 的按钮与 `aria-label`，三个视觉组件（`LaneVisual` / `TerminalMock` / `LanePathGraphic`）里模拟应用界面的字，以及 `release.ts::formatSize` 的「约 X MB」。
- `index.html` 写死中文 `lang` / `title` / `description` / `og:*` / `twitter:*` / JSON-LD；`sitemap.xml` 只有 `/` 一条。
- 现有代码里所有 `window` / `navigator` 调用都在 `onMounted` 内，**天然 SSR 安全**，迁到 vite-ssg 不需要改运行逻辑。

同生态的 `keeper-website` 已有一套跑通的双语实现（vite-ssg 预渲染 `/` 与 `/en/`、provide/inject 的 i18n、unhead 按语言输出 head）。本次直接继承其结论，不重新趟坑。

## 目标

官网出中英两个语言版本，各有独立 URL、各自预渲染成完整 HTML、可被搜索引擎分别收录。

## 已确认的四项决策

1. **形态**：独立 URL + 预渲染（vue-router + vite-ssg），不是纯前端切换、也不是 `?lang=` 查询参数。
2. **默认语言**：中文在 `/`，英文在 `/en/`；`x-default` 指 `/`。**不按浏览器语言自动跳转**，只在用户手动切换后记住偏好。
3. **模拟界面**：英文页里三个视觉组件的界面文字**跟着切英文**。
4. **范围**：只做官网。桌面端 i18n、管理端、第三种语言都不在本次。

## 一、路由与页面结构

引入 `vue-router` + `vite-ssg` + `@unhead/vue` 三个依赖。

```
routes = [
  { path: "/",   component: HomePage },   // 中文
  { path: "/en", component: HomePage },   // 英文
]
```

同一个页面组件的两个语言版本，locale 由路由派生。

- 新建 `src/pages/HomePage.vue`：装现有 9 个区块（Hero / Lane / Verify / Terminal / Steps / Download / Faq 及其顺序注释）。
- `src/App.vue` 瘦身为 `TheHeader` + `<RouterView />` + `TheFooter` + `useHead`。
- `src/main.ts` 从 `createApp(App).mount()` 改为 `export const createApp = ViteSSG(App, { routes })`，字体 import 原样保留。
- `vite.config.ts` 加 `ssgOptions: { dirStyle: "nested" }`——产物是 `dist/en/index.html`（目录 + index）；默认的 `flat` 会输出 `dist/en.html`，与「URL 不带 .html」的路径对不上。

**SSR 安全约束**（新增代码必须遵守）：所有在模块顶层或 `setup` 里执行的代码在构建期都没有 `window` / `document` / `navigator` / `localStorage`。浏览器 API 一律进 `onMounted`。

## 二、i18n 层（`src/i18n.ts`）

```
export type Locale = "zh" | "en";
localeFromPath(path): Locale      // 语言的单一来源是 URL：/en 或 /en/** → en，其余 → zh
localePath(l): string             // zh → "/"，en → "/en/"
rememberLocale(l) / savedLocale() // localStorage，key = "lane-locale"
provideI18n()                     // 仅 App.vue setup 调用一次
useI18n()                         // 子组件取用，返回 { locale, t, htmlLang }
```

**硬约束：locale 用 provide/inject 挂在 app 实例上，禁止做成模块单例。** vite-ssg 会在同一进程里**并发**预渲染多条路由，模块级可变状态会被其它路由的渲染改写（keeper-website 曾因此让中文页序列化出英文 head）。每个 app 实例一份才天然隔离。

`t` 是 `computed<Copy>(() => COPY[locale.value])`，组件里写 `t.title` 这样取。

**回访偏好**：`App.vue` 的 `onMounted` 里，若当前在 `/` 且 `savedLocale() === "en"`，`router.replace("/en/")`。三条限制：只做 `/ → /en/` 单向（显式访问 `/en/` 永远尊重 URL）；只认手动切换写入的偏好，不读 `navigator.language`；放在 `onMounted`（水合完成后）而非 setup，避免首帧路由变化造成水合失配。

## 三、文案（`src/content/copy.ts`）

重构为强类型的双份结构：

```
export interface Copy { meta; nav; hero; lane; verify; terminal; steps; download; faq; footer; ui; }
const zh: Copy = { ... };
const en: Copy = { ... };
export const COPY: Record<Locale, Copy> = { zh, en };
```

`interface Copy` 是中英结构一致的**强制手段**——漏翻任何一个字段，`vue-tsc`（`lint-website`）就报错，不靠人工核对。

现有导出（`nav` / `hero` / `lane` / ...）全部并入 `Copy`，并新增两节：

- `meta: { title, description }`——给 `App.vue` 的 `useHead` 用。
- `ui`——收纳原先散在模板里的零碎串（见下）。

### 需要从模板/逻辑里收进 copy.ts 的中文

| 位置 | 内容 |
|---|---|
| `TheHeader.vue` | 「下载」CTA、`aria-label="主导航"`、`aria-label="MintPop Lane 首页"` |
| `HeroSection.vue` | 按钮三态（`前往下载` / `下载 macOS 版` / `下载 Windows 版`）、「全部安装包」、「最新版本」 |
| `DownloadSection.vue` | 「最新版本」、「正在获取最新版本…」、「Apple 芯片（M 系列）」、「x64 安装器」 |
| `TheFooter.vue` | `aria-label="页脚导航"` |
| `LaneVisual.vue` | 窗口 `aria-label`、链路已接通、会话、＋新建会话、专属链路、出口正常可以开始了、终端井两行 |
| `TerminalMock.vue` | 井 `aria-label`、「链路正常」 |
| `LanePathGraphic.vue` | 三个节点名（本机 / 专属链路 / 出口）、三个 hint、五种状态的 `aria` 串 |
| `release.ts` | `formatSize` 的「约 X MB」 |

`formatSize` 是**纯函数且有单测**。处理方式：把「约」这个语言相关的前缀移出纯函数（纯函数只返回 `"32 MB"`，前缀由调用方按语言拼），并同步修改 `release.test.ts` 中的断言。不在纯函数里塞 locale 参数——那会让一个与语言无关的换算函数凭空长出语言依赖。

### 英文文案的写法

**不做直译。** 沿用 `copy.ts` 顶部已有的自查原则（「每句话都问『读者看完会想那跟我有什么关系吗』，会就删掉重写」），用地道英文重写。中文那版口语化的取向（说用户能感觉到的好处，不解释实现机制）在英文里同样成立。

**允许的内容差异**：结构必须一致（同为 5 条），但同一槽位的内容可以按读者调整。已确认的一处：FAQ 第 5 条中文是「在中国大陆能用吗」，英文版换成 **"Do I need my own API key or proxy?"**（订阅额度与链路都已内置，不必自备）——这是国际读者在同一位置真正会问的问题。其余 4 条一一对应，「支持 Intel Mac 吗」两版都保留。

## 四、head 与 SEO

`index.html` **只留与语言无关的静态项**：`charset`、`viewport`、`theme-color`、`preconnect`、`icon` / `apple-touch-icon`、`og:type` / `og:site_name` / `og:image*` / `twitter:card`。

其余全部移入 `App.vue` 的 `useHead`，按语言输出：

- `htmlAttrs.lang`：`zh-CN` / `en`
- `title`、`meta[name=description]`
- `og:title` / `og:description` / `og:url` / `og:locale`（`zh_CN` / `en_US`）
- `twitter:title` / `twitter:description`
- `link rel=canonical` → 当前语言版的绝对 URL
- `hreflang` 三连：`zh-CN` → `/`、`en` → `/en/`、`x-default` → `/`
- JSON-LD：`name` / `description` / `url` / `inLanguage` 随语言。**现在 `inLanguage` 写死 `zh-CN`，不改的话英文页会带中文结构化数据。**

`public/sitemap.xml` 加 `/en/` 一条，与 `/` 并列。

## 五、语言切换 UI

顶栏加一个 `中 / EN` 文字按钮，点击即 `rememberLocale(next)` + `router.push(localePath(next))`。

**位置硬约束：放在 `.nav` 外面、紧邻下载 CTA。** 现有样式在 `@media (max-width: 860px)` 下把 `.nav` 整个 `display: none`，切换按钮若放在 nav 里，手机上就切不了语言——而手机恰恰是英文访客最可能的入口。

## 六、构建与部署

- mise 的 `build-website` 由 `pnpm vite build` 改为 `pnpm vite-ssg build`。
- **Dockerfile 与 CI 零改动**：两者都调 `mise run build-website` / `lint-website` / `test-website`，命令收口到 mise 的好处在此兑现。
- `preview-website`（`pnpm vite preview`）继续对 `dist` 生效，不需要改。

### nginx（待实测确认，不当既成事实写死）

预计**不需要改** `nginx.conf`：现有 `try_files $uri $uri/ =404` 配 `index index.html`，请求 `/en/` 时 `$uri` 作为文件不存在、`$uri/` 命中目录，再由 index 指令取到 `/en/index.html`；请求 `/en`（无尾斜杠）由 nginx 自行 301 补斜杠。

**这是推断，实施时必须用 `mise run image-website` 起容器实测 `/en/`、`/en`、`/`、以及一个不存在路径仍返回 404，再下结论。** 若实测不通，则在 `nginx.conf` 里补一条针对 `/en/` 的 `location`，并在实施记录里写明原因。

## 七、测试与验收

- 新增 `src/i18n.test.ts`：`localeFromPath`（`/`、`/en`、`/en/`、`/other`）、`localePath`、`savedLocale`（合法值 / 非法值 / 未设置）。纯函数，node 环境，沿用现有 vitest 配置，不引 jsdom。
- `release.test.ts` 随 `formatSize` 的改动同步。
- 中英结构一致性由 `interface Copy` 在 `lint-website`（`vue-tsc --noEmit`）里保证，不另写运行时测试。
- **人工验收**（build 后对产物执行，不是看 dev server）：
  1. `dist/en/index.html` 的**正文含英文实体文本**——证明预渲染真的把内容写进了 HTML，不是空壳靠 JS 填。
  2. `dist/index.html` 与 `dist/en/index.html` 的 `lang` / `title` / `canonical` / `og:locale` / JSON-LD `inLanguage` 各自正确，且**中文页里不含任何英文 head 串**（这是 SSG 并发预渲染串状态的典型症状，专门查一遍）。
  3. 两份 HTML 的 `hreflang` 三连一致。
  4. 顶栏切换在 <860px 视口下仍可见可点。

## 明确不做

- **桌面端 i18n**：桌面端目前完全没有 i18n、界面全中文。本次英文页的模拟界面显示英文，意味着它展示的界面在真实桌面端尚不存在——这是已确认的取舍，建议单独立项排后续计划。
- 管理端 `apps/admin`。
- 第三种语言（结构上不排斥，`Locale` 加成员即可，但本次不做）。
- `/en/` 之外的新页面（使用说明、法律页等）。
