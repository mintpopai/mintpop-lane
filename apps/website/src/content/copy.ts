// 官网文案集中一处：区块组件只读这里，不在模板里散写长句。
//
// 写法取向：**说用户能感觉到的好处，不解释我们怎么实现的**。
// 出口探测、凭据注入顺序、验签这些机制该待在代码和 CLAUDE.md 里，
// 落地页只回答一件事——用了它，你能少折腾什么。
//
// 一条自查：每句话都问「读者看完会想『那跟我有什么关系』吗」，会就删掉重写。

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
  title: "专属线路，独立出口",
  lede: "多个账号共用一个出口，是账号异常最常见的原因。Lane 为每个账号分配独立线路和固定出口，不与他人共用。",
  points: [
    {
      title: "不用自己配网络",
      body: "不装代理软件、不填服务器地址、不导入配置文件。登录完，路就已经通了。",
    },
    {
      title: "出口固定不变",
      body: "每次连上都是同一个出口，不会今天一个地址、明天一个地址。地址老跳，本身就容易被当成异常。",
    },
    {
      title: "线路的事我们管",
      body: "调整、维护、续期都在我们这边处理，你这边什么都不用改。",
    },
  ],
} as const;

/** 出错时说人话：同一种情况，别处只丢一句话，Lane 会说清下一步 */
export const verify = {
  kicker: "状态可诊断",
  title: "出问题时，它会说清卡在哪一步",
  lede: "多数工具只给一句「连接失败」，剩下的你自己排查。Lane 会指明是哪一段没通、该等一下还是该你动手。",
  caption: "同一种情况，两种说法。",
  /** 对照组：别处出错时，你能拿到的全部信息就这么多 */
  others: {
    tag: "常见的做法",
    code: "Error: connection failed",
    note: "是网络、是代理，还是账号出了问题？没有下文，只能一个个试。",
  },
  /** Lane：同一件事，说清楚了 */
  lane: {
    tag: "Lane",
    badge: "已暂停",
    label: "出口对不上，已暂停",
    detail: "实际走的出口和分配给你的不一致，为了安全先停下了。点「重新连接」重走一遍就行。",
    retry: "重新连接",
    note: "发生了什么、接下来做什么，都写在上面。",
  },
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
