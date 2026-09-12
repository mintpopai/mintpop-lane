// 指南页的内容模型。每篇指南一个文件，导出 Record<Locale, GuideCopy>；
// interface 强制中英字段齐全，数组条数由 guides.test.ts 的结构比对兜底。
//
// 写法取向与 copy.ts 一致：先给结论，说用户能感觉到的，不解释实现。
// 每篇只押一组搜索意图（见 docs/website-seo.md 的关键词地图），不要一篇里什么都讲。
import type { Locale } from "../../i18n";

export interface GuideSection {
  /** h2 */
  heading: string;
  /** 段落，纯文本；允许为空数组（只有 bullets 的小节） */
  paragraphs: string[];
  /** 可选无序列表，排在段落之后 */
  bullets?: string[];
}

export interface GuideCopy {
  /** <title> 与 description，可以与 h1 不同（title 带关键词与站名，h1 给人读） */
  meta: { title: string; description: string };
  /** 页脚与首页列表里的短标题 */
  navLabel: string;
  /** 首页「延伸阅读」里的一句摘要 */
  summary: string;
  /** 面包屑下的小标签，如「指南」 */
  kicker: string;
  h1: string;
  /** 引言段，视觉上加重 */
  intro: string;
  /** ISO 日期 YYYY-MM-DD，进 Article.datePublished / dateModified */
  published: string;
  updated: string;
  sections: GuideSection[];
  /** 页内 FAQ，同时输出 FAQPage 结构化数据 */
  faq: { q: string; a: string }[];
  /** 页尾 CTA，链到本语言首页的下载区 */
  cta: { title: string; body: string; label: string };
}

export interface Guide {
  /** URL 段，只含 [a-z0-9-]；中英共用同一 slug */
  slug: string;
  copy: Record<Locale, GuideCopy>;
}
