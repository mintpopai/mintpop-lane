// 押的意图：「Claude 账号被封」「Claude 封号 原因」「Claude 账号异常」
import type { Locale } from "../../i18n";
import type { GuideCopy } from "./types";

const zh: GuideCopy = {
  meta: {
    title: "Claude 账号为什么会被封？常见原因与避免方法 | MintPop Lane",
    description:
      "Claude 账号被封、被限制的常见诱因：共享出口、出口地址频繁变化、支付信息与使用地区不一致、非官方客户端。附自查清单，以及 MintPop Lane 如何用独立固定出口与自营 IP 把风险降到最低。",
  },
  navLabel: "封号原因与避免",
  summary: "共享出口、地址跳动、支付不一致、非官方客户端：逐条讲清，附一份自查清单。",
  kicker: "指南",
  h1: "Claude 账号为什么会被封，怎么避免",
  intro:
    "账号被封时你看到的只有一封邮件，看不到原因。但从大量案例往回看，诱因高度集中在几件事上，而且大多和你写的代码无关。",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "最常见的四个诱因",
      paragraphs: [],
      bullets: [
        "共用出口：一个出口地址上挂着许多账号，任何一个触发风控，整段地址上的账号都会被连带审视。这是账号异常最常见的原因。",
        "出口地址频繁变化：今天从一个地址登录、明天换另一个，看起来就像账号在被多人共享或倒卖。",
        "支付信息与使用地区不一致：账单地址在一个国家、登录出口在另一个国家、卡又是第三个地方发的。",
        "非官方客户端与逆向接口：通过镜像站、逆向或第三方接口调用，本身就不在官方允许的使用方式之内。",
      ],
    },
    {
      heading: "一份自查清单",
      paragraphs: ["每一条答「否」，都是一个风险点。多数人至少会中两条。"],
      bullets: [
        "你的出口是否只有你一个人在用？",
        "最近一周的出口地址是否固定？",
        "订阅支付所用的卡与账单地址，和你登录时的地区是否一致？",
        "你用的是官方 Claude Code，还是某个中转？",
        "同一台设备上是否只登录过这一个账号？",
      ],
    },
    {
      heading: "Lane 怎么规避这几条",
      paragraphs: ["MintPop Lane 的做法是把风险点从你这边挪走，而不是教你怎么小心。"],
      bullets: [
        "每个账号一条独立链路、一个固定出口，不与他人共用；出口是我们自有美国公司的自营 IP，非第三方转租、非共享池。",
        "出口固定不变：每次连上都是同一个地址，不会今天一个、明天一个。",
        "账号与支付由我司统一代办，账单信息与出口地区一致。",
        "全程 100% 官方 Claude Code，不经过任何镜像或中转。",
        "实际出口与分配的出口对不上时，应用会先暂停并告诉你，而不是带着异常继续跑。",
      ],
    },
    {
      heading: "真被封了怎么办",
      paragraphs: [
        "先别急着注册新账号再用同一条网络，那是把风险点原样复制一遍。",
        "在 Lane 里，如发生封号，经内部调查并走官方申诉流程后，按未使用天数比例退还剩余费用（需扣除 30 美元云服务器、IP 使用及备案等必要杂项成本）。",
      ],
    },
  ],
  faq: [
    {
      q: "封号和我写什么代码有关吗？",
      a: "绝大多数案例与代码内容无关，诱因集中在出口、支付与客户端这几件事上。Lane 也不读取或上传你的项目文件。",
    },
    {
      q: "换个代理节点就能解决吗？",
      a: "恰恰相反，频繁更换出口地址本身就是风控信号。要解决的是「固定且独享」，不是「多换几个」。",
    },
    {
      q: "Lane 能保证不被封吗？",
      a: "没有人能保证。Lane 做的是把已知的高风险因素逐条移除，并给出明确的售后承诺。",
    },
  ],
  cta: {
    title: "让出口只属于你",
    body: "下载 MintPop Lane，每个账号一条独立链路。",
    label: "前往下载",
  },
};

const en: GuideCopy = {
  meta: {
    title: "Why Claude accounts get banned, and how to avoid it | MintPop Lane",
    description:
      "The common triggers behind Claude account bans and restrictions: shared exits, exit addresses that keep changing, billing details that do not match the region of use, and unofficial clients. Includes a self-check list and how MintPop Lane cuts the risk with a dedicated fixed exit on IPs we operate.",
  },
  navLabel: "Why accounts get banned",
  summary:
    "Shared exits, moving addresses, mismatched billing, unofficial clients: each one explained, plus a self-check list.",
  kicker: "Guide",
  h1: "Why Claude accounts get banned, and how to avoid it",
  intro:
    "When an account is banned, all you see is an email. The reason is never in it. Looking back across many cases, though, the triggers cluster around a handful of things, and most of them have nothing to do with the code you write.",
  published: "2026-09-12",
  updated: "2026-09-12",
  sections: [
    {
      heading: "The four most common triggers",
      paragraphs: [],
      bullets: [
        "A shared exit: many accounts behind one exit address. When any of them trips risk controls, every account on that address gets looked at. This is the single most common cause.",
        "An exit address that keeps changing: signing in from one address today and another tomorrow looks like an account being shared or resold.",
        "Billing details that do not match where you use it: a billing address in one country, a login exit in another, and a card issued in a third.",
        "Unofficial clients and reverse-engineered access: mirror sites, reverse-engineered endpoints and third-party APIs are outside the ways the service is meant to be used.",
      ],
    },
    {
      heading: "A self-check list",
      paragraphs: ["Every answer of no is a risk point. Most people hit at least two."],
      bullets: [
        "Is your exit used by you alone?",
        "Has your exit address stayed the same over the past week?",
        "Do the card and billing address behind your subscription match the region you sign in from?",
        "Are you on official Claude Code, or on a relay?",
        "Has only this one account ever signed in on this device?",
      ],
    },
    {
      heading: "How Lane avoids each of them",
      paragraphs: [
        "MintPop Lane removes the risk points from your side instead of asking you to be careful.",
      ],
      bullets: [
        "Every account gets its own lane and its own fixed exit, never shared. The exit runs on IPs operated by our own US company, not rented from a third party and not a shared pool.",
        "The exit never moves: every connection lands on the same address, not one today and another tomorrow.",
        "We handle the account and the payment, so billing details match the exit region.",
        "100% official Claude Code the whole way, with no mirror or relay in between.",
        "If the actual exit ever fails to match the one assigned to you, the app pauses and tells you, rather than carrying on with something wrong.",
      ],
    },
    {
      heading: "If you do get banned",
      paragraphs: [
        "Do not rush to register a new account on the same network. That copies the risk point exactly as it was.",
        "With Lane, if an account is banned, after an internal review and the official appeal process we refund the remaining fee pro rata by unused days, minus a $30 fixed cost for the cloud server, IP and registration overhead.",
      ],
    },
  ],
  faq: [
    {
      q: "Does it depend on what code I write?",
      a: "In the vast majority of cases, no. The triggers cluster around the exit, the payment and the client. Lane also never reads or uploads your project files.",
    },
    {
      q: "Will switching proxy nodes fix it?",
      a: "The opposite. Changing exit addresses often is itself a risk signal. What you need is fixed and exclusive, not more of them.",
    },
    {
      q: "Can Lane guarantee I will not be banned?",
      a: "Nobody can. What Lane does is remove the known high-risk factors one by one, and back it with a clear guarantee.",
    },
  ],
  cta: {
    title: "An exit that belongs to you alone",
    body: "Download MintPop Lane. One dedicated lane per account.",
    label: "Go to download",
  },
};

export const whyClaudeAccountsGetBanned: Record<Locale, GuideCopy> = { zh, en };
