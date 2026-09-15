import { describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { mountWithI18n, readHead } from "../testing";
import FaqSection from "./FaqSection.vue";

describe("FaqSection", () => {
  it("用原生 details：无 JS 也能展开，爬虫也读得到答案正文", async () => {
    const { wrapper } = await mountWithI18n(FaqSection);

    const items = wrapper.findAll("details");
    expect(items).toHaveLength(COPY.zh.faq.items.length);
    expect(items[0].element.tagName).toBe("DETAILS");
    expect(items.map((d) => d.get("summary .q").text())).toEqual(COPY.zh.faq.items.map((i) => i.q));
    // 答案在 DOM 里，不是点开才渲染
    expect(wrapper.text()).toContain(COPY.zh.faq.items[0].a);
  });

  it("配了延伸阅读的条目才出「→」链接，没配的不留空链接", async () => {
    const { wrapper } = await mountWithI18n(FaqSection);

    const withMore = COPY.zh.faq.items.filter((i) => i.more);
    const links = wrapper.findAll(".more");
    expect(links).toHaveLength(withMore.length);
    expect(links.map((a) => a.attributes("href"))).toEqual(withMore.map((i) => i.more!.href));
  });

  it("输出 FAQPage 结构化数据，问答与页面上看到的逐条一致", async () => {
    const { head } = await mountWithI18n(FaqSection);

    const [ld] = (await readHead(head)).jsonLd;
    expect(ld["@type"]).toBe("FAQPage");
    expect(ld.inLanguage).toBe("zh-CN");
    expect(ld.mainEntity).toEqual(
      COPY.zh.faq.items.map((i) => ({
        "@type": "Question",
        name: i.q,
        acceptedAnswer: { "@type": "Answer", text: i.a },
      })),
    );
  });

  it("英文页的结构化数据换英文问答与 inLanguage", async () => {
    const { head, wrapper } = await mountWithI18n(FaqSection, { locale: "en" });

    const [ld] = (await readHead(head)).jsonLd;
    expect(ld.inLanguage).toBe("en");
    expect((ld.mainEntity as { name: string }[])[0].name).toBe(COPY.en.faq.items[0].q);
    expect(wrapper.get(".section-title").text()).toBe(COPY.en.faq.title);
  });
});
