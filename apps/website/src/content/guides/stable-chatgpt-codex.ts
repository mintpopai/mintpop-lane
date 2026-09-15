// 押的意图：「ChatGPT 稳定使用」「ChatGPT 账号被封」「Codex 国内」
// 硬约束：如实说明 Lane 当前只接入 Claude Code、Codex 在规划中且无日期，不许写出任何时间表。
import type { Locale } from "../../i18n";
import type { GuideCopy } from "./types";

const zh: GuideCopy = {
  meta: {
    title: "ChatGPT / Codex 稳定使用指南：支付、出口与封号问题怎么解 | MintPop Lane",
    description:
      "ChatGPT 与 Codex 用户在国内遇到的问题和 Claude 一样：订阅付不出去、出口不稳定、账号被封。本文讲清原理与应对方式，并如实说明 MintPop Lane 当前接入的是 Claude Code，Codex 接入在规划中。",
  },
  navLabel: "ChatGPT / Codex",
  summary:
    "ChatGPT / Codex 的稳定性问题与 Claude 同源；Lane 当前接入 Claude Code，Codex 在规划中。",
  kicker: "指南",
  h1: "想稳定使用 ChatGPT / Codex？先看清卡在哪",
  intro:
    "先把话说在前面：MintPop Lane 现在接入的是 Claude Code，ChatGPT / Codex 的接入在规划中。之所以还写这篇，是因为两边的问题几乎一样，理解了原理，选哪家都少走弯路。",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "ChatGPT 用户遇到的，和 Claude 用户是同一批问题",
      paragraphs: [],
      bullets: [
        "订阅支付：Plus / Pro 订阅同样需要海外卡与对应地区的账单信息。",
        "网络出口：无论是网页版 ChatGPT 还是终端里的 Codex，每次请求都要经过一个境外出口；出口共用、地址跳动一样会触发风控。",
        "账号封禁：批量注册、共享出口、支付信息不一致，都是两家平台共同盯着的信号。",
      ],
    },
    {
      heading: "为什么「换个工具」解决不了",
      paragraphs: [
        "很多人被封后从 ChatGPT 转到 Claude，或者反过来，结果在同一条网络、同一张卡上再次出问题。问题不在平台，在你接入平台的方式：出口是否独享且固定、支付与地区是否一致、用的是不是官方客户端。",
        "把接入方式做对，换哪家都稳；接入方式不对，换哪家都一样。",
      ],
    },
    {
      heading: "Lane 现在能为你做什么",
      paragraphs: [
        "如果你的目标是在终端里稳定跑一个编码 Agent，Lane 今天就能给你官方 Claude Code：账号与订阅代办、独立固定的自营美国出口、装好一切的内置终端。原本用 Codex 的开发者，在这里可以直接换成 Claude Code 开工。",
        "Codex 接入上线后会自动出现在应用里，你这边不用升级或改配置。想第一时间知道，可以通过页脚的联系方式告诉我们。",
      ],
    },
  ],
  faq: [
    {
      q: "现在能在 Lane 里用 ChatGPT 或 Codex 吗？",
      a: "还不能。当前只接入 Claude Code，Codex 在规划中，上线后应用内自动出现。",
    },
    {
      q: "Codex 什么时候上？",
      a: "暂无确定日期。我们不承诺时间表，上线时会在官网与应用内同时公布。",
    },
    {
      q: "Claude Code 和 Codex 做同一件事吗？",
      a: "都是跑在终端里的编码 Agent，能读写你的项目、执行命令、改代码。日常开发中两者的用法非常接近。",
    },
  ],
  cta: {
    title: "今天就能稳定用的编码 Agent",
    body: "下载 MintPop Lane，官方 Claude Code 已经配好。",
    label: "前往下载",
  },
};

const en: GuideCopy = {
  meta: {
    title: "Stable ChatGPT / Codex access: payment, exit and bans explained | MintPop Lane",
    description:
      "ChatGPT and Codex users in China hit the same wall as Claude users: subscriptions that will not go through, unstable exits, banned accounts. This guide explains why and what to do, and says plainly that MintPop Lane currently ships Claude Code, with Codex support planned.",
  },
  navLabel: "ChatGPT / Codex",
  summary:
    "ChatGPT / Codex stability problems share one root with Claude's. Lane ships Claude Code today; Codex is planned.",
  kicker: "Guide",
  h1: "Want stable ChatGPT / Codex access? First see where it breaks",
  intro:
    "Up front: MintPop Lane currently ships Claude Code, and ChatGPT / Codex support is planned. This guide exists anyway because the problems on both sides are nearly identical, and once you understand why, either choice gets easier.",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "ChatGPT users hit the same problems Claude users do",
      paragraphs: [],
      bullets: [
        "Paying for the subscription: Plus and Pro need an overseas card and billing details for a matching region, just like Claude.",
        "The network exit: whether it is ChatGPT in the browser or Codex in the terminal, every request goes out through an exit outside China. A shared exit or a moving address trips risk controls the same way.",
        "Account bans: bulk sign-ups, shared exits and mismatched billing details are signals both platforms watch.",
      ],
    },
    {
      heading: "Why switching tools does not fix it",
      paragraphs: [
        "Plenty of people move from ChatGPT to Claude after a ban, or the other way round, and get burned again on the same network and the same card. The platform is not the problem. How you connect to it is: whether the exit is exclusive and fixed, whether payment matches the region, whether you are on an official client.",
        "Get the connection right and either platform is stable. Get it wrong and neither is.",
      ],
    },
    {
      heading: "What Lane can do for you today",
      paragraphs: [
        "If the goal is a coding agent that runs reliably in your terminal, Lane gives you official Claude Code today: account and subscription handled, a dedicated fixed US exit we operate, and a built-in terminal with everything installed. Developers coming from Codex can switch to Claude Code here and get to work.",
        "When Codex support ships it will appear inside the app on its own, with no upgrade or configuration on your side. If you want to hear first, tell us through the contact link in the footer.",
      ],
    },
  ],
  faq: [
    {
      q: "Can I use ChatGPT or Codex in Lane right now?",
      a: "Not yet. Lane currently ships Claude Code only. Codex is planned and will appear in the app when it ships.",
    },
    {
      q: "When is Codex coming?",
      a: "No fixed date. We do not promise timelines. When it ships we will announce it on the site and inside the app at the same time.",
    },
    {
      q: "Do Claude Code and Codex do the same job?",
      a: "Both are coding agents that run in a terminal: they read and write your project, run commands and change code. Day to day, using one feels very close to using the other.",
    },
  ],
  cta: {
    title: "A coding agent you can rely on today",
    body: "Download MintPop Lane. Official Claude Code, already set up.",
    label: "Go to download",
  },
};

export const stableChatgptCodex: Record<Locale, GuideCopy> = { zh, en };
