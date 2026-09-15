import { describe, expect, it } from "vitest";
import { GUIDES } from "../content/guides";
import { COPY } from "../content/copy";
import { SITE } from "../routes";
import { mountWithI18n, readHead } from "../testing";
import GuidePage from "./GuidePage.vue";

const GUIDE = GUIDES[0];
const ZH_PATH = `/guides/${GUIDE.slug}`;
const EN_PATH = `/en/guides/${GUIDE.slug}`;

function ldOf(all: Record<string, unknown>[], type: string) {
  return all.find((x) => x["@type"] === type);
}

describe("GuidePage 正文", () => {
  it("按 URL 里的 slug 取对应那篇", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: ZH_PATH });

    expect(wrapper.get("h1").text()).toBe(GUIDE.copy.zh.h1);
    expect(wrapper.get(".intro").text()).toBe(GUIDE.copy.zh.intro);
  });

  it("小节按注册表逐条渲染，带 bullets 的才出列表", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: ZH_PATH });

    const blocks = wrapper.findAll(".block:not(.faq)");
    const sections = GUIDE.copy.zh.sections;
    expect(blocks).toHaveLength(sections.length);
    expect(blocks.map((b) => b.get("h2").text())).toEqual(sections.map((s) => s.heading));

    const withBullets = sections.filter((s) => s.bullets).length;
    expect(wrapper.findAll(".block:not(.faq) ul")).toHaveLength(withBullets);
  });

  it("更新日期用 <time datetime>，机器能读", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: ZH_PATH });

    const time = wrapper.get(".updated time");
    expect(time.attributes("datetime")).toBe(GUIDE.copy.zh.updated);
    expect(time.text()).toBe(GUIDE.copy.zh.updated);
  });

  it("页内 FAQ 同样用原生 details", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: ZH_PATH });

    const items = wrapper.findAll(".faq details");
    expect(items).toHaveLength(GUIDE.copy.zh.faq.length);
    expect(items[0].get("summary").text()).toBe(GUIDE.copy.zh.faq[0].q);
  });

  it("面包屑与页尾 CTA 都回本语言首页", async () => {
    const zh = await mountWithI18n(GuidePage, { path: ZH_PATH });
    expect(zh.wrapper.get(".crumbs a").attributes("href")).toBe("/");
    expect(zh.wrapper.get(".cta .btn").attributes("href")).toBe("/#download");

    const en = await mountWithI18n(GuidePage, { path: EN_PATH });
    expect(en.wrapper.get(".crumbs a").attributes("href")).toBe("/en/");
    expect(en.wrapper.get(".cta .btn").attributes("href")).toBe("/en/#download");
  });

  it("面包屑末节是当前页、不做成链接（点自己没有意义）", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: ZH_PATH });

    const crumbs = wrapper.get(".crumbs");
    expect(crumbs.attributes("aria-label")).toBe("breadcrumb");
    expect(crumbs.findAll("a")).toHaveLength(1);
    expect(crumbs.text()).toContain(GUIDE.copy.zh.navLabel);
  });

  it("英文页整篇换英文", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: EN_PATH });

    expect(wrapper.get("h1").text()).toBe(GUIDE.copy.en.h1);
    expect(wrapper.get(".faq h2").text()).toBe(COPY.en.ui.guide.faqTitle);
  });

  it("slug 不在注册表里就整页不渲染，交给 nginx 去返 404", async () => {
    const { wrapper } = await mountWithI18n(GuidePage, { path: "/guides/no-such-guide" });

    expect(wrapper.find("main").exists()).toBe(false);
  });
});

describe("GuidePage 的结构化数据", () => {
  it("Article 段带正文标题、两个日期与作者", async () => {
    const { head } = await mountWithI18n(GuidePage, { path: ZH_PATH });
    const ld = ldOf((await readHead(head)).jsonLd, "Article")!;

    expect(ld.headline).toBe(GUIDE.copy.zh.h1);
    expect(ld.datePublished).toBe(GUIDE.copy.zh.published);
    expect(ld.dateModified).toBe(GUIDE.copy.zh.updated);
    expect(ld.inLanguage).toBe("zh-CN");
    expect(ld.mainEntityOfPage).toBe(`${SITE}/guides/${GUIDE.slug}/`);
    expect(ld.author).toMatchObject({ "@type": "Organization", name: "MintPop" });
  });

  it("配图按语言分两张：英文页不该配中文排版的那张", async () => {
    const zh = await mountWithI18n(GuidePage, { path: ZH_PATH });
    const zhLd = ldOf((await readHead(zh.head)).jsonLd, "Article")!;
    expect(zhLd.image).toBe(`${SITE}/og.png`);

    const en = await mountWithI18n(GuidePage, { path: EN_PATH });
    const enLd = ldOf((await readHead(en.head)).jsonLd, "Article")!;
    expect(enLd.image).toBe(`${SITE}/og-en.png`);
  });

  it("面包屑结构化数据两级：首页 › 本篇，与页面上看到的一致", async () => {
    const { head } = await mountWithI18n(GuidePage, { path: EN_PATH });
    const ld = ldOf((await readHead(head)).jsonLd, "BreadcrumbList")!;

    expect(ld.itemListElement).toEqual([
      {
        "@type": "ListItem",
        position: 1,
        name: COPY.en.ui.guide.home,
        item: `${SITE}/en/`,
      },
      {
        "@type": "ListItem",
        position: 2,
        name: GUIDE.copy.en.h1,
        item: `${SITE}/en/guides/${GUIDE.slug}/`,
      },
    ]);
  });

  it("页内问答同时输出 FAQPage，条数与正文一致", async () => {
    const { head } = await mountWithI18n(GuidePage, { path: ZH_PATH });
    const ld = ldOf((await readHead(head)).jsonLd, "FAQPage")!;

    expect(ld.mainEntity).toEqual(
      GUIDE.copy.zh.faq.map((f) => ({
        "@type": "Question",
        name: f.q,
        acceptedAnswer: { "@type": "Answer", text: f.a },
      })),
    );
  });

  it("标题与描述来自本篇 meta，并同步给社交卡片", async () => {
    const { head } = await mountWithI18n(GuidePage, { path: ZH_PATH });
    const { title, meta } = await readHead(head);

    expect(title).toBe(GUIDE.copy.zh.meta.title);
    expect(meta.description).toBe(GUIDE.copy.zh.meta.description);
    expect(meta["og:title"]).toBe(GUIDE.copy.zh.meta.title);
    expect(meta["twitter:description"]).toBe(GUIDE.copy.zh.meta.description);
  });

  it("未知 slug 不输出任何结构化数据与标题，免得给 404 页留下半份 SEO", async () => {
    const { head } = await mountWithI18n(GuidePage, { path: "/guides/no-such-guide" });
    const { title, jsonLd, meta } = await readHead(head);

    expect(jsonLd).toHaveLength(0);
    // 空标题不会被 unhead 渲染成 <title></title>，而是整条不出——正是想要的
    expect(title).toBeUndefined();
    expect(meta.description).toBeUndefined();
  });

  it("四篇指南都能按自己的 slug 打开，注册表加一篇不用改路由", async () => {
    for (const g of GUIDES) {
      const { wrapper } = await mountWithI18n(GuidePage, { path: `/guides/${g.slug}` });
      expect(wrapper.get("h1").text()).toBe(g.copy.zh.h1);
    }
  });
});
