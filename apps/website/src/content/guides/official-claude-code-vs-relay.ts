// 押的意图：「Claude 镜像站」「Claude 中转 API」「官方 Claude Code 区别」
import type { Locale } from "../../i18n";
import type { GuideCopy } from "./types";

const zh: GuideCopy = {
  meta: {
    title: "官方 Claude Code 与镜像站、中转 API 的区别：模型、限流、隐私、稳定性 | MintPop Lane",
    description:
      "想稳定用 Claude，先分清三种接入方式：官方 Claude Code、镜像站、中转 API。本文从模型一致性、限流策略、隐私、稳定性与价格五个维度对比，并说明 MintPop Lane 为什么只做官方接入。",
  },
  navLabel: "官方与镜像对比",
  summary: "五个维度对比三种接入方式，说清为什么「省事」不该以「不是官方」为代价。",
  kicker: "指南",
  h1: "官方 Claude Code、镜像站与中转 API 有什么区别",
  intro:
    "搜「Claude 稳定使用」时你会看到三类东西：官方 Claude Code、各种镜像站、各种中转 API。它们解决的不是同一个问题，价格差异也不是凭空来的。",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "三种方式分别是什么",
      paragraphs: [],
      bullets: [
        "官方 Claude Code：Anthropic 发布的命令行 Agent，跑在你本机终端里，直接连官方服务，额度来自你的订阅。",
        "镜像站：第三方搭的网页或客户端，背后转发到某个账号池，你用的是它的账号、它的出口。",
        "中转 API：第三方给你一个 API 地址与密钥，你把 Claude Code 或别的工具指向它，请求先到它的服务器再转出去。",
      ],
    },
    {
      heading: "五个维度对比",
      paragraphs: [],
      bullets: [
        "模型与功能：官方接入拿到的模型版本、功能权限与官方完全一致；镜像与中转由对方决定给你什么，新模型、新功能不一定同步。",
        "限流策略：官方按你的订阅规格限流，用量可在官方后台查到；中转的限流由对方账号池的状态决定，高峰时段常常排队或失败。",
        "隐私：官方接入时对话只在你与官方之间；镜像与中转会经过第三方服务器，代码与对话是否留存你无法核对。",
        "稳定性：账号池一旦被批量封禁，镜像与中转会整体不可用；官方接入的稳定性取决于你自己的出口与账号是否干净。",
        "价格：中转看起来便宜，是因为成本被摊到共享账号与共享出口上，而这正是最容易出问题的地方。",
      ],
    },
    {
      heading: "MintPop Lane 为什么只做官方接入",
      paragraphs: [
        "Lane 不是镜像站，也不是中转 API。它做的是把「官方接入」需要的三样东西替你准备好：一个由我司代为注册与支付的官方账号与订阅、一条独立且固定的自营美国出口、一个已经装好 Claude Code 的内置终端。",
        "你终端里跑的是 100% 官方 Claude Code，模型版本、功能权限、限流策略与官方完全一致，用量可以同步官方后台查看。我们不对对话内容做存储或留存。",
      ],
    },
    {
      heading: "什么情况选哪种",
      paragraphs: [],
      bullets: [
        "只想偶尔试试、不在乎模型版本与隐私：镜像站够用。",
        "已经有稳定的海外账号与干净出口、只是想省 API 费用：中转 API 可以考虑，但要接受隐私与稳定性的代价。",
        "要把 Claude Code 当日常生产力工具、不想被封号与掉线打断：官方接入，把账号、出口、环境交给 Lane。",
      ],
    },
  ],
  faq: [
    {
      q: "Lane 是中转吗？",
      a: "不是。你的 Claude Code 直接连官方服务，Lane 只负责账号、订阅、专属出口与本机环境，不经手你的对话内容。",
    },
    {
      q: "用量怎么核对？",
      a: "Lane 里能实时查看，也可以与官方后台的用量对照，两边是一致的。",
    },
    {
      q: "能用自己的 API key 吗？",
      a: "不需要也不用填。席位里的额度会自动接到内置终端。",
    },
  ],
  cta: {
    title: "要官方的，就用官方的",
    body: "下载 MintPop Lane，100% 官方 Claude Code。",
    label: "前往下载",
  },
};

const en: GuideCopy = {
  meta: {
    title:
      "Official Claude Code vs mirror sites vs relay APIs: models, rate limits, privacy, stability | MintPop Lane",
    description:
      "Before chasing stable Claude access, know the three ways in: official Claude Code, mirror sites, and relay APIs. A comparison across model parity, rate limits, privacy, stability and price, and why MintPop Lane only does official access.",
  },
  navLabel: "Official vs relay",
  summary: "Three ways in, compared on five dimensions, and why convenience should not cost you the official product.",
  kicker: "Guide",
  h1: "Official Claude Code, mirror sites and relay APIs: what is the difference",
  intro:
    "Search for stable Claude access and you will find three kinds of thing: official Claude Code, mirror sites, and relay APIs. They do not solve the same problem, and the price gaps are not accidental.",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "What each one is",
      paragraphs: [],
      bullets: [
        "Official Claude Code: the command-line agent published by Anthropic. It runs in your local terminal, talks to the official service directly, and draws usage from your subscription.",
        "Mirror site: a web page or client built by a third party, forwarding to a pool of accounts behind it. You are using their accounts and their exit.",
        "Relay API: a third party gives you an API endpoint and a key. You point Claude Code or another tool at it, and every request goes to their server first.",
      ],
    },
    {
      heading: "Five dimensions",
      paragraphs: [],
      bullets: [
        "Model and features: official access gives you exactly the official model versions and feature access. Mirrors and relays decide what you get, and new models or features do not necessarily arrive.",
        "Rate limits: official limits follow your subscription, and usage shows in the official dashboard. A relay's limits follow the state of its account pool, and peak hours often mean queues or failures.",
        "Privacy: with official access the conversation stays between you and the service. Mirrors and relays route it through a third party's servers, and you cannot verify what is kept.",
        "Stability: when an account pool gets banned in bulk, a mirror or relay goes down as a whole. Official access is as stable as your own exit and account are clean.",
        "Price: relays look cheap because the cost is spread across shared accounts and shared exits, which is exactly where things break.",
      ],
    },
    {
      heading: "Why MintPop Lane only does official access",
      paragraphs: [
        "Lane is not a mirror and not a relay. It prepares the three things official access needs: an official account and subscription we register and pay for, a dedicated fixed US exit we operate, and a built-in terminal with Claude Code already installed.",
        "What runs in your terminal is 100% official Claude Code, with model versions, feature access and rate limits identical to the official ones, and usage you can check against the official dashboard. We do not store or retain conversations.",
      ],
    },
    {
      heading: "Which one, when",
      paragraphs: [],
      bullets: [
        "Just trying things out, not fussed about model version or privacy: a mirror site is enough.",
        "Already have a stable overseas account and a clean exit, only want to save on API cost: a relay is an option, if you accept the privacy and stability cost.",
        "Claude Code as a daily tool, without bans and drops getting in the way: official access, with the account, exit and environment handled by Lane.",
      ],
    },
  ],
  faq: [
    {
      q: "Is Lane a relay?",
      a: "No. Your Claude Code talks to the official service directly. Lane only handles the account, subscription, dedicated exit and local environment, and never touches your conversations.",
    },
    {
      q: "How do I verify usage?",
      a: "It shows live inside Lane, and it can be checked against the official dashboard. The two match.",
    },
    {
      q: "Can I use my own API key?",
      a: "You do not need one and there is nothing to paste. The usage in your seat attaches to the built-in terminal automatically.",
    },
  ],
  cta: {
    title: "If you want official, use official",
    body: "Download MintPop Lane. 100% official Claude Code.",
    label: "Go to download",
  },
};

export const officialClaudeCodeVsRelay: Record<Locale, GuideCopy> = { zh, en };
