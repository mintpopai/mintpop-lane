// 押的意图：「国内怎么用 Claude Code」「Claude Code 国内 稳定」「Claude 国内 订阅 支付」
import type { Locale } from "../../i18n";
import type { GuideCopy } from "./types";

const zh: GuideCopy = {
  meta: {
    title: "国内怎么稳定使用 Claude Code：支付、网络出口与账号风控一次讲清 | MintPop Lane",
    description:
      "在中国大陆使用 Claude Code 会卡在三处：订阅支付、网络出口、账号风控。本文讲清每一处为什么会出问题、自己折腾的代价，以及 MintPop Lane 如何把账号、订阅、专属出口一站配好，让你稳定使用官方 Claude Code。",
  },
  navLabel: "国内使用指南",
  summary: "支付、出口、风控三道坎逐一讲清，以及怎么绕开它们、稳定用上官方 Claude Code。",
  kicker: "指南",
  h1: "国内怎么稳定使用 Claude Code",
  intro:
    "在国内用 Claude Code，难的不是安装，而是让它一直能用：订阅付不出去、网络时通时断、账号莫名被封。这篇把三道坎逐一讲清，再说怎么一次配好、之后不用再管。",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "三道坎：支付、出口、风控",
      paragraphs: [
        "第一道是支付。Claude 的订阅需要海外信用卡与对应地区的账单信息，国内常见的卡与支付方式大多走不通；即便走通了，支付信息与登录地区对不上，本身也是风控信号之一。",
        "第二道是网络出口。Claude Code 是跑在你本机终端里的 Agent，每一次调用都要经过一个境外出口。这个出口是否干净、是否固定、是否与别人共用，直接决定它今天能不能用、明天还能不能用。",
        "第三道是账号风控。账号异常最常见的原因不是「你做了什么」，而是「你和谁共用了出口」：一个出口地址上挂着几十个账号，其中任何一个出问题，整段地址都会被盯上。",
      ],
    },
    {
      heading: "自己折腾，代价在哪",
      paragraphs: [
        "每一道坎都能自己过，但每一道都要持续投入时间，而且互相牵连：改了一处，另一处就可能出问题。",
      ],
      bullets: [
        "代理软件与订阅：要自己选节点、配规则、盯着它别掉线；节点一换，出口地址就跳，正是风控最敏感的信号。",
        "海外卡与账单地址：办卡、养卡、填地址，每一步都可能失败；成功了也要担心支付信息与实际使用地区不一致。",
        "环境变量与密钥：装 Node、装 Claude Code、配代理变量、保管密钥，任何一处改动都可能让终端里的 Agent 突然不工作。",
        "出了问题没法定位：多数工具只给一句「连接失败」，是网络、代理还是账号，只能挨个试。",
      ],
    },
    {
      heading: "镜像站与中转 API 的取舍",
      paragraphs: [
        "镜像站与中转 API 的好处是省事，代价是你拿到的不一定是官方 Claude Code：模型版本、功能权限、限流策略都由中转方决定，用量也看不到官方后台的数据。",
        "更要紧的是隐私。你的代码与对话会经过第三方服务器，对方是否留存、怎么用，你无从核对。想把 Claude Code 当日常生产力工具，这两条代价通常不值得。",
      ],
    },
    {
      heading: "用 MintPop Lane：一次配好，之后不用再管",
      paragraphs: [
        "MintPop Lane 是一站式的官方 Claude Code 接入软件，把上面三道坎都挪到我们这边：海外账号与订阅由我司代为注册和支付；每个账号分配一条独立链路和固定的美国出口，不与他人共用；应用自带终端，Claude Code 与依赖已经装好，额度自动接上，不用填密钥。",
        "全程用的是 100% 官方 Claude Code，非镜像、非逆向，用量可以同步官方后台查看。对话内容不做存储或留存。",
      ],
      bullets: [
        "下载安装：macOS（Apple 芯片）或 Windows x64，装完打开。",
        "登录：点一下登录，在浏览器里确认后自动跳回，第一次会顺手建好账号。登录这一步跟随你自己的系统代理，之后的流量走专属出口。",
        "开写：选一个项目目录，终端里输入 claude 就能开始。",
      ],
    },
    {
      heading: "席位与售后",
      paragraphs: [
        "两种席位用的都是同一套官方 Claude Code 与自营美国出口，只差用量规格：Standard 席位 $40（原价 $50），用量为 1.25 倍 PRO；Premium 席位 $160（原价 $200），用量为 6.25 倍 PRO。",
        "如发生封号，经内部调查并走官方申诉流程后，按未使用天数比例退还剩余费用（需扣除 30 美元云服务器、IP 使用及备案等必要杂项成本）。",
      ],
    },
  ],
  faq: [
    {
      q: "登录时需要自己开代理吗？",
      a: "登录这一步会跟着你自己的系统代理走；登录完成后，Claude Code 的流量走 Lane 分配给你的专属出口，不再依赖你的代理。",
    },
    {
      q: "需要自己准备海外信用卡吗？",
      a: "不需要。海外账号与信用卡由我司代为注册和支付，你只需要在 Lane 里登录。",
    },
    {
      q: "用的是官方 Claude Code 吗？",
      a: "是。非镜像、非逆向、非第三方接口，模型版本、功能权限、限流策略与官方一致，可同步官方后台用量。",
    },
  ],
  cta: {
    title: "把三道坎交给我们",
    body: "下载 MintPop Lane，登录一次，选个目录就能开始。",
    label: "前往下载",
  },
};

const en: GuideCopy = {
  meta: {
    title:
      "How to use Claude Code from China reliably: payment, network exit and account risk | MintPop Lane",
    description:
      "Using Claude Code from mainland China breaks in three places: paying for the subscription, the network exit, and account risk controls. This guide explains each one, what it costs to handle them yourself, and how MintPop Lane sets up the account, subscription and a dedicated exit so official Claude Code just keeps working.",
  },
  navLabel: "Claude Code from China",
  summary:
    "Payment, exit and risk controls explained one by one, and how to get past them with official Claude Code.",
  kicker: "Guide",
  h1: "How to use Claude Code from China, reliably",
  intro:
    "Installing Claude Code in China is easy. Keeping it working is not: the subscription will not go through, the network drops, the account gets banned for no visible reason. This guide walks through the three hurdles, then shows how to set things up once and stop thinking about them.",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "Three hurdles: payment, exit, risk controls",
      paragraphs: [
        "First, payment. A Claude subscription needs an overseas card and billing details for a matching region. Most cards and payment methods common in China do not go through, and even when they do, billing details that do not match the login region are themselves a risk signal.",
        "Second, the network exit. Claude Code is an agent running in your local terminal, and every call goes out through an exit outside China. Whether that exit is clean, fixed, and not shared with anyone else decides whether it works today and still works tomorrow.",
        "Third, account risk controls. The most common cause of account trouble is not what you did, but who you share an exit with: dozens of accounts on one exit address, and when any of them trips a wire, the whole address gets scrutinised.",
      ],
    },
    {
      heading: "Doing it yourself: where the cost is",
      paragraphs: [
        "Each hurdle can be cleared on your own, but each one keeps demanding time, and they interact: fix one thing and another breaks.",
      ],
      bullets: [
        "Proxy software and subscriptions: pick nodes, write rules, watch for drops. Every node change moves your exit address, which is exactly the signal risk controls watch most closely.",
        "Overseas cards and billing addresses: getting a card, keeping it alive, filling in an address. Every step can fail, and success still leaves billing details that do not match where you actually use it.",
        "Environment variables and keys: install Node, install Claude Code, set proxy variables, keep keys safe. Any change can make the agent in your terminal stop working.",
        "No way to locate a failure: most tools give you one line, connection failed, and leave you to try the network, the proxy and the account one by one.",
      ],
    },
    {
      heading: "Mirror sites and relay APIs: the trade-off",
      paragraphs: [
        "Mirror sites and relay APIs are convenient. The price is that what you get is not necessarily official Claude Code: model version, feature access and rate limits are whatever the relay decides, and you never see the usage the official dashboard would show.",
        "Privacy matters more. Your code and conversations pass through a third party's servers, and you have no way to verify what they keep or how they use it. If Claude Code is meant to be a daily tool, neither cost is usually worth it.",
      ],
    },
    {
      heading: "With MintPop Lane: set up once, stop managing it",
      paragraphs: [
        "MintPop Lane is an all-in-one client for official Claude Code that moves all three hurdles to our side: we register and pay for the overseas account and subscription; every account gets its own lane with a fixed US exit, never shared; the app ships with a terminal where Claude Code and its dependencies are already installed, with usage attached automatically and no keys to paste.",
        "It is 100% official Claude Code the whole way, no mirror and no reverse engineering, and usage can be checked against the official dashboard. Nothing you type is stored.",
      ],
      bullets: [
        "Install: macOS (Apple silicon) or Windows x64. Install, open.",
        "Sign in: one click, confirm in the browser, and you are sent back. The first sign-in creates your account. This step follows your own system proxy; everything after it runs through your dedicated exit.",
        "Start: pick a project folder, type claude in the terminal, go.",
      ],
    },
    {
      heading: "Seats and our guarantee",
      paragraphs: [
        "Both seats run the same official Claude Code over the same US exit we operate ourselves. The only difference is usage: Standard is $40 (was $50) at 1.25× PRO usage; Premium is $160 (was $200) at 6.25× PRO usage.",
        "If an account is banned, after an internal review and the official appeal process we refund the remaining fee pro rata by unused days, minus a $30 fixed cost for the cloud server, IP and registration overhead.",
      ],
    },
  ],
  faq: [
    {
      q: "Do I need my own proxy to sign in?",
      a: "Sign-in follows your own system proxy. Once you are in, Claude Code traffic runs through the dedicated exit Lane assigned to you and no longer depends on your proxy.",
    },
    {
      q: "Do I need an overseas credit card?",
      a: "No. We register and pay for the overseas account and card. You only sign in inside Lane.",
    },
    {
      q: "Is it official Claude Code?",
      a: "Yes. No mirror, no reverse engineering, no third-party API. Model version, feature access and rate limits match the official ones, and usage can be checked against the official dashboard.",
    },
  ],
  cta: {
    title: "Hand the three hurdles to us",
    body: "Download MintPop Lane, sign in once, pick a folder and start.",
    label: "Go to download",
  },
};

export const claudeCodeInChina: Record<Locale, GuideCopy> = { zh, en };
