// 全站文案：中英两份，结构由 interface Copy 强制一致——漏翻任何字段 vue-tsc 直接报错，
// 不靠人工核对。组件只经 useI18n() 的 t 取文案，不在模板里内联硬编码。
//
// 写法取向：**说用户能感觉到的好处，不解释我们怎么实现的**。
// 出口探测、凭据注入顺序、验签这些机制该待在代码和 CLAUDE.md 里，
// 落地页只回答一件事——用了它，你能少折腾什么。
//
// 一条自查：每句话都问「读者看完会想『那跟我有什么关系』吗」，会就删掉重写。
// 英文版**不做直译**：中文那版口语化的取向在英文里照样成立，按英文读者重写。

/** 终端 mock 里逐行打出来的内容 */
export interface TerminalLine {
  kind: "prompt" | "dim" | "out" | "cursor";
  text: string;
}

/** 链路图的五种处境。定义在文案文件里，是因为 aria 串要按它逐个配 key（见 ui.path.aria） */
export type LaneVariant = "active" | "connecting" | "unreachable" | "mismatch" | "off";

export interface Copy {
  /** <title> 与 meta description/og，由 App.vue 的 useHead 按语言输出 */
  meta: { title: string; description: string };
  nav: { href: string; label: string }[];
  hero: { pill: string; title: [string, string]; lede: string; note: string };
  lane: { kicker: string; title: string; lede: string; points: { title: string; body: string }[] };
  verify: {
    kicker: string;
    title: string;
    lede: string;
    caption: string;
    /** 对照组：别处出错时，你能拿到的全部信息就这么多 */
    others: { tag: string; code: string; note: string };
    /** Lane：同一件事，说清楚了 */
    lane: {
      tag: string;
      badge: string;
      label: string;
      detail: string;
      retry: string;
      note: string;
    };
  };
  terminal: {
    kicker: string;
    title: string;
    lede: string;
    points: { title: string; body: string }[];
    lines: TerminalLine[];
  };
  steps: { kicker: string; title: string; items: { title: string; body: string }[] };
  download: {
    kicker: string;
    title: string;
    requirements: { platform: string; body: string }[];
    note: string;
    unavailable: string;
  };
  faq: { kicker: string; title: string; items: { q: string; a: string }[] };
  footer: { tagline: string; links: { href: string; label: string }[] };
  /** 原先散在模板与视觉组件里的零碎串，按出现位置分组收在这里 */
  ui: {
    header: {
      cta: string;
      navLabel: string;
      homeLabel: string;
      langToggle: string;
      langSwitchLabel: string;
    };
    hero: {
      primaryFallback: string;
      primaryMac: string;
      primaryWin: string;
      allDownloads: string;
      latest: string;
    };
    download: {
      latest: string;
      loading: string;
      archMac: string;
      archWin: string;
      /** 体积前缀，拼在 formatSize 的裸值前面（约 32 MB / ~32 MB） */
      sizePrefix: string;
    };
    footer: { navLabel: string };
    lane: { caption: string };
    visual: {
      ariaLabel: string;
      status: string;
      railHead: string;
      newSession: string;
      cardTitle: string;
      noteStrong: string;
      noteBody: string;
    };
    terminalMock: { ariaLabel: string; state: string };
    path: {
      names: [string, string, string];
      hints: [string, string, string];
      aria: Record<LaneVariant, string>;
    };
  };
}

const zh: Copy = {
  meta: {
    title: "MintPop Lane · 打开就能写代码",
    description:
      "打开就能写代码，别的都替你办好了。代理、密钥、运行环境这些准备工作 MintPop Lane 都替你做完，登录一次挑个目录就能开始。支持 macOS（Apple 芯片）与 Windows。",
  },
  nav: [
    { href: "#lane", label: "专属链路" },
    { href: "#terminal", label: "内置终端" },
    { href: "#faq", label: "常见问题" },
  ],
  hero: {
    pill: "支持 macOS 与 Windows",
    title: ["打开就能写代码，", "别的都替你办好了。"],
    lede: "用 AI 编码工具之前，代理、密钥、运行环境总得先折腾一遍。这些 Lane 都替你做完了，登录一次，挑个项目目录，就能开始。",
    note: "使用需要账号与有效订阅。",
  },
  lane: {
    kicker: "专属链路",
    title: "专属链路，独立出口",
    lede: "多个账号共用一个出口，是账号异常最常见的原因。Lane 为每个账号分配独立链路和固定出口，不与他人共用。",
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
        title: "链路的事我们管",
        body: "调整、维护、续期都在我们这边处理，你这边什么都不用改。",
      },
    ],
  },
  verify: {
    kicker: "状态可诊断",
    title: "出问题时，它会说清卡在哪一步",
    lede: "多数工具只给一句「连接失败」，剩下的你自己排查。Lane 会指明是哪一段没通、该等一下还是该你动手。",
    caption: "同一种情况，两种说法。",
    others: {
      tag: "常见的做法",
      code: "Error: connection failed",
      note: "是网络、是代理，还是账号出了问题？没有下文，只能一个个试。",
    },
    lane: {
      tag: "Lane",
      badge: "已暂停",
      label: "出口对不上，已暂停",
      detail: "实际走的出口和分配给你的不一致，为了安全先停下了。点「重新连接」重走一遍就行。",
      retry: "重新连接",
      note: "发生了什么、接下来做什么，都写在上面。",
    },
  },
  terminal: {
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
    lines: [
      { kind: "prompt", text: "claude" },
      { kind: "dim", text: "链路已就绪 · 额度已接入" },
      { kind: "out", text: "欢迎回来，从哪里开始？" },
      { kind: "cursor", text: "" },
    ],
  },
  steps: {
    kicker: "三步开始",
    title: "从下载到写下第一行",
    items: [
      { title: "装上", body: "挑你的系统下载，装完打开。" },
      {
        title: "登录",
        body: "点一下登录，在浏览器里确认完就自动跳回来。第一次用会顺手把账号建好。",
      },
      { title: "开写", body: "链路通了、额度接上了，选个目录就能开工。" },
    ],
  },
  download: {
    kicker: "下载",
    title: "下载 MintPop Lane",
    requirements: [
      { platform: "macOS", body: "macOS 12 及以上，Apple 芯片（M 系列）。不支持 Intel Mac。" },
      { platform: "Windows", body: "Windows 10 / 11，x64。" },
    ],
    note: "使用需要账号与有效订阅，第一次登录会顺手把账号建好。",
    unavailable: "下载链接暂时不可用，请稍后重试。",
  },
  faq: {
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
  },
  footer: {
    tagline: "Pop into something fresh.",
    links: [
      { href: "#download", label: "下载" },
      { href: "#faq", label: "常见问题" },
      { href: "#lane", label: "专属链路" },
    ],
  },
  ui: {
    header: {
      cta: "下载",
      navLabel: "主导航",
      homeLabel: "MintPop Lane 首页",
      langToggle: "中 / EN",
      langSwitchLabel: "切换到英文",
    },
    hero: {
      primaryFallback: "前往下载",
      primaryMac: "下载 macOS 版",
      primaryWin: "下载 Windows 版",
      allDownloads: "全部安装包",
      latest: "最新版本",
    },
    download: {
      latest: "最新版本",
      loading: "正在获取最新版本…",
      archMac: "Apple 芯片（M 系列）",
      archWin: "x64 安装器",
      sizePrefix: "约 ",
    },
    footer: { navLabel: "页脚导航" },
    lane: { caption: "应用里能一直看到链路的状态，哪一段没通，就在哪一段上标出来。" },
    visual: {
      ariaLabel: "MintPop Lane 应用界面示意：链路已接通，终端里正在运行 Agent 会话",
      status: "链路已接通",
      railHead: "会话",
      newSession: "＋ 新建会话",
      cardTitle: "专属链路",
      noteStrong: "链路已接通",
      noteBody: "出口正常，可以开始了。",
    },
    terminalMock: {
      ariaLabel: "内置终端示意：链路已接通，正在运行 Agent 会话",
      state: "链路正常",
    },
    path: {
      names: ["本机", "专属链路", "出口"],
      hints: ["你的电脑", "专门给你的", "只有你在走"],
      aria: {
        active: "链路状态：已接通",
        connecting: "链路状态：正在接通",
        unreachable: "链路状态：连不上",
        mismatch: "链路状态：出口对不上",
        off: "链路状态：还没分配链路",
      },
    },
  },
};

const en: Copy = {
  meta: {
    title: "MintPop Lane · Open it and start coding",
    description:
      "Open it and start coding — everything else is already handled. MintPop Lane sets up the network, the keys, and the runtime for you. Sign in once, pick a folder, and go. macOS (Apple silicon) and Windows.",
  },
  nav: [
    { href: "#lane", label: "Dedicated lane" },
    { href: "#terminal", label: "Built-in terminal" },
    { href: "#faq", label: "FAQ" },
  ],
  hero: {
    pill: "macOS and Windows",
    title: ["Open it and start coding.", "Everything else is handled."],
    lede: "Before you can use an AI coding tool, there is always a round of setup: the network, the keys, the runtime. Lane has done all of it. Sign in once, pick a project folder, and you are writing code.",
    note: "An account and an active subscription are required.",
  },
  lane: {
    kicker: "Dedicated lane",
    title: "Your own lane, your own exit",
    lede: "Sharing one exit across many accounts is the most common reason accounts get flagged. Lane gives every account its own path and its own fixed exit — never shared.",
    points: [
      {
        title: "No network setup",
        body: "No proxy app to install, no server address to type, no config file to import. By the time you are signed in, the route is already up.",
      },
      {
        title: "A fixed exit",
        body: "The same exit every time you connect — not one address today and another tomorrow. An address that keeps jumping is itself what gets you flagged.",
      },
      {
        title: "We keep it running",
        body: "Tuning, maintenance, renewals — all handled on our side. Nothing on yours ever needs to change.",
      },
    ],
  },
  verify: {
    kicker: "Diagnosable state",
    title: "When something breaks, it tells you where",
    lede: "Most tools give you one line — connection failed — and leave the rest to you. Lane points at the segment that is down and tells you whether to wait or to act.",
    caption: "Same situation, two ways of putting it.",
    others: {
      tag: "The usual",
      code: "Error: connection failed",
      note: "Network? Proxy? Account? Nothing follows, so you try things one at a time.",
    },
    lane: {
      tag: "Lane",
      badge: "Paused",
      label: "Exit does not match — paused",
      detail:
        "The exit you are actually going through is not the one assigned to you, so we stopped for safety. Hit Reconnect to run it again.",
      retry: "Reconnect",
      note: "What happened, and what to do next — both right there.",
    },
  },
  terminal: {
    kicker: "Built-in terminal",
    title: "Pick a folder and go",
    lede: "Everything the agent needs is already set up. Choose a project folder and start.",
    points: [
      {
        title: "Nothing to install",
        body: "The first time you use it, it installs everything for you — no docs to dig through, no environment variables to edit.",
      },
      {
        title: "No keys to paste",
        body: "Your subscription's credits connect automatically. No more keeping a key in your clipboard and on a sticky note.",
      },
      {
        title: "Several projects at once",
        body: "One tab per project. Switch freely — the one you leave keeps running in the background.",
      },
      {
        title: "A real terminal",
        body: "Unicode, emoji, and full-color TUIs all render properly. It behaves like the terminal you already use.",
      },
    ],
    lines: [
      { kind: "prompt", text: "claude" },
      { kind: "dim", text: "Lane ready · credits connected" },
      { kind: "out", text: "Welcome back. Where should we start?" },
      { kind: "cursor", text: "" },
    ],
  },
  steps: {
    kicker: "Three steps",
    title: "From download to your first line",
    items: [
      { title: "Install", body: "Download the build for your system, install it, open it." },
      {
        title: "Sign in",
        body: "Click sign in, confirm in your browser, and you are back. First time through, your account gets created along the way.",
      },
      {
        title: "Start writing",
        body: "The lane is up and your credits are connected — pick a folder and get to work.",
      },
    ],
  },
  download: {
    kicker: "Download",
    title: "Download MintPop Lane",
    requirements: [
      {
        platform: "macOS",
        body: "macOS 12 or later, Apple silicon (M-series). Intel Macs are not supported.",
      },
      { platform: "Windows", body: "Windows 10 / 11, x64." },
    ],
    note: "An account and an active subscription are required; your account is created on first sign-in.",
    unavailable: "Download links are temporarily unavailable. Please try again shortly.",
  },
  faq: {
    kicker: "FAQ",
    title: "Still wondering",
    items: [
      {
        q: "Can I use it without a subscription?",
        a: "You can download it and sign in, but you need credits before you can start writing. When there are none, the app tells you directly — no guessing.",
      },
      {
        q: "Which agents are supported?",
        a: "Claude Code today. Anything added later shows up in the app on its own — nothing for you to upgrade or reconfigure.",
      },
      {
        q: "Do you support Intel Macs?",
        a: "No. The macOS build is Apple silicon only.",
      },
      {
        q: "Is my code safe?",
        a: "Lane neither reads nor uploads your project files; it only gets the network and the credits ready. Where your code goes depends on what you ask the agent to do in the terminal.",
      },
      {
        q: "Do I need my own API key or proxy?",
        a: "No. Your subscription's credits and your dedicated lane are both built in — there is no key to paste and no proxy to configure.",
      },
    ],
  },
  footer: {
    tagline: "Pop into something fresh.",
    links: [
      { href: "#download", label: "Download" },
      { href: "#faq", label: "FAQ" },
      { href: "#lane", label: "Dedicated lane" },
    ],
  },
  ui: {
    header: {
      cta: "Download",
      navLabel: "Main navigation",
      homeLabel: "MintPop Lane home",
      langToggle: "EN / 中",
      langSwitchLabel: "Switch to Chinese",
    },
    hero: {
      primaryFallback: "Go to downloads",
      primaryMac: "Download for macOS",
      primaryWin: "Download for Windows",
      allDownloads: "All downloads",
      latest: "Latest version",
    },
    download: {
      latest: "Latest version",
      loading: "Fetching the latest version…",
      archMac: "Apple silicon (M-series)",
      archWin: "x64 installer",
      sizePrefix: "~",
    },
    footer: { navLabel: "Footer navigation" },
    lane: {
      caption:
        "The app shows lane status at all times — whichever segment is down is marked right there.",
    },
    visual: {
      ariaLabel:
        "MintPop Lane interface illustration: the lane is connected and an agent session is running in the terminal",
      status: "Lane connected",
      railHead: "Sessions",
      newSession: "＋ New session",
      cardTitle: "Dedicated lane",
      noteStrong: "Lane connected",
      noteBody: "Exit checks out. You are good to go.",
    },
    terminalMock: {
      ariaLabel:
        "Built-in terminal illustration: the lane is connected and an agent session is running",
      state: "Lane OK",
    },
    path: {
      names: ["Your machine", "Your lane", "Exit"],
      hints: ["Where you work", "Assigned to you", "Yours alone"],
      aria: {
        active: "Lane status: connected",
        connecting: "Lane status: connecting",
        unreachable: "Lane status: unreachable",
        mismatch: "Lane status: exit mismatch",
        off: "Lane status: no lane assigned",
      },
    },
  },
};

export const COPY: Record<"zh" | "en", Copy> = { zh, en };
