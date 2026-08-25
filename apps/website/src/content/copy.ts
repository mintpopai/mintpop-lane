// 官网文案集中一处：区块组件只读这里，不在模板里散写长句。
//
// 写法取向：**说用户能感觉到的好处，不解释我们怎么实现的**。
// 出口探测、凭据注入顺序、验签这些机制该待在代码和 CLAUDE.md 里，
// 落地页只回答一件事——用了它，你能少折腾什么。
//
// 一条自查：每句话都问「读者看完会想『那跟我有什么关系』吗」，会就删掉重写。

/** 状态基调：与桌面端 link.ts 的 Tone 同名同义，颜色永远配文字使用 */
export type Tone = "ok" | "warn" | "danger" | "muted";

export const nav = [
  { href: "#lane", label: "专属线路" },
  { href: "#terminal", label: "内置终端" },
  { href: "#faq", label: "常见问题" },
] as const;

export const hero = {
  pill: "支持 macOS 与 Windows",
  title: ["打开就能写代码，", "别的都替你办好了。"],
  lede: "用 AI 编码工具之前，代理、密钥、运行环境总得先折腾一遍。这些 Lane 都替你做完了，登录一次，挑个项目目录，就能开始。",
  note: "使用需要账号与有效订阅。",
} as const;

/** 专属线路 */
export const lane = {
  kicker: "专属线路",
  title: "你有自己的一条路，不跟别人挤",
  lede: "很多人挤在同一个出口，是账号出问题最常见的原因。Lane 给每个账号单独一条线路、一个固定出口，别人碰不到你的。",
  points: [
    {
      title: "不用自己配网络",
      body: "不装代理软件、不填服务器地址、不导入配置文件。登录完，路就已经通了。",
    },
    {
      title: "出口只有你在用",
      body: "你的流量不和别人混在一起，账号被别人牵连的可能性从一开始就小得多。",
    },
    {
      title: "线路的事我们管",
      body: "调整、维护、续期都在我们这边处理，你这边什么都不用改。",
    },
  ],
} as const;

/** 状态一目了然 */
export const verify = {
  kicker: "状态一目了然",
  title: "真出了问题，它会告诉你是哪儿的问题",
  lede: "大多数工具只会甩你一句「连接失败」，然后你自己查半天。Lane 会说清楚现在卡在哪一步、是该等一下还是该动手。",
  /** 四种典型状态。措辞与应用里显示的一致，只是这里说得更白话一点 */
  situations: [
    {
      label: "还没给你分配线路",
      detail: "管理员配好之后，点一下就能用。",
      tone: "muted" as Tone,
      retry: "重新检查",
      why: "不是你的问题，等一下就行。",
    },
    {
      label: "线路连不上",
      detail: "多半是你这边的网络或者代理设置，换个网络试试。",
      tone: "danger" as Tone,
      retry: "重新连接",
      why: "这个确实得你动手。",
    },
    {
      label: "出口对不上",
      detail: "实际走的出口和分给你的不一样，为了安全先停下了。",
      tone: "warn" as Tone,
      retry: "重新连接",
      why: "宁可停一下，也不让你在不对的路上跑。",
    },
    {
      label: "一切正常",
      detail: "可以开始写代码了。",
      tone: "ok" as Tone,
      retry: "",
      why: "到这儿就没你什么事了。",
    },
  ],
} as const;

/** 内置终端 */
export const terminal = {
  kicker: "内置终端",
  title: "挑个目录，直接开始",
  lede: "Agent 要用的东西都已经配好。你只需要选一个项目目录，点开始。",
  points: [
    {
      title: "不用自己装",
      body: "第一次用的时候它替你装好，不用去翻文档、改环境变量。",
    },
    {
      title: "不用填密钥",
      body: "订阅里的额度自动接上，不必再把一串 key 存在剪贴板和便签里。",
    },
    {
      title: "几个项目一起开",
      body: "一个项目一个标签页，来回切互不打扰，切走的那个照样在后台跑。",
    },
    {
      title: "就是个正经终端",
      body: "中文、emoji、彩色界面都正常显示，跟你平时用的终端没什么两样。",
    },
  ],
  /** 终端 mock 里逐行打出来的内容 */
  lines: [
    { kind: "prompt" as const, text: "claude" },
    { kind: "dim" as const, text: "线路已就绪 · 额度已接入" },
    { kind: "out" as const, text: "欢迎回来，从哪里开始？" },
    { kind: "cursor" as const, text: "" },
  ],
} as const;

/** 三步开始 */
export const steps = {
  kicker: "三步开始",
  title: "从下载到写下第一行",
  items: [
    {
      title: "装上",
      body: "挑你的系统下载，装完打开。",
    },
    {
      title: "登录",
      body: "点一下登录，在浏览器里确认完就自动跳回来。第一次用会顺手把账号建好。",
    },
    {
      title: "开写",
      body: "线路通了、额度接上了，选个目录就能开工。",
    },
  ],
} as const;

/** 下载 */
export const download = {
  kicker: "下载",
  title: "下载 MintPop Lane",
  requirements: [
    { platform: "macOS", body: "macOS 12 及以上，Apple 芯片（M 系列）。不支持 Intel Mac。" },
    { platform: "Windows", body: "Windows 10 / 11，x64。" },
  ],
  note: "使用需要账号与有效订阅，第一次登录会顺手把账号建好。",
  unavailable: "下载链接暂时不可用，请稍后重试。",
} as const;

/** 常见问题 */
export const faq = {
  kicker: "常见问题",
  title: "还想知道的",
  items: [
    {
      q: "没有订阅能用吗？",
      a: "能下载、能登录，但要有额度才能开始写。没有可用额度的时候，应用里会直接告诉你，不用猜。",
    },
    {
      q: "支持哪些 Agent？",
      a: "目前是 Claude Code。以后新增的会自动出现在应用里，你这边不用升级或者改配置。",
    },
    {
      q: "支持 Intel Mac 吗？",
      a: "不支持，macOS 只出 Apple 芯片版本。",
    },
    {
      q: "我的代码安全吗？",
      a: "Lane 不读也不上传你的项目文件，它只负责把网络和额度准备好。代码去哪，取决于你在终端里让 Agent 做什么。",
    },
    {
      q: "在中国大陆能用吗？",
      a: "能。登录这一步会跟着你自己的系统代理走，之后的流量走你的专属出口。",
    },
  ],
} as const;

export const footer = {
  tagline: "Pop into something fresh.",
  links: [
    { href: "#download", label: "下载" },
    { href: "#faq", label: "常见问题" },
    { href: "#lane", label: "专属线路" },
  ],
} as const;
