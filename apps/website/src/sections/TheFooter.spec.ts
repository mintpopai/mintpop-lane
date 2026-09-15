import { describe, expect, it } from "vitest";
import { GUIDES } from "../content/guides";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import TheFooter from "./TheFooter.vue";

describe("TheFooter", () => {
  it("深底配白字版词标：品牌规范不许把深字版硬放在深色上", async () => {
    const { wrapper } = await mountWithI18n(TheFooter);

    const src = wrapper.get(".brand img").attributes("src");
    expect(src).toContain("mintpop-wordmark-light");
    expect(src).not.toContain("wordmark-dark");
  });

  it("页内锚点带语言前缀，指南页上也指得回本语言首页", async () => {
    const { wrapper } = await mountWithI18n(TheFooter, {
      path: "/en/guides/claude-code-in-china",
    });

    const anchors = wrapper
      .findAll("nav.links a")
      .map((a) => a.attributes("href"))
      .filter((h) => h?.startsWith("/en/") && h.includes("#"));
    expect(anchors).toEqual(COPY.en.footer.links.map((l) => `/en/${l.href}`));
  });

  it("四篇指南每页都链到，这是搜索引擎发现它们的主要路径", async () => {
    const { wrapper } = await mountWithI18n(TheFooter);

    const hrefs = wrapper.findAll("nav.links a").map((a) => a.attributes("href"));
    for (const g of GUIDES) expect(hrefs).toContain(`/guides/${g.slug}/`);
  });

  it("英文页的指南内链带 /en 前缀、标题换英文", async () => {
    const { wrapper } = await mountWithI18n(TheFooter, { locale: "en" });

    const links = wrapper.findAll("nav.links a");
    const hrefs = links.map((a) => a.attributes("href"));
    for (const g of GUIDES) expect(hrefs).toContain(`/en/guides/${g.slug}/`);
    expect(links.map((a) => a.text())).toContain(GUIDES[0].copy.en.navLabel);
  });

  it("联系入口与页头同一份文案，同样另开标签页", async () => {
    const { wrapper } = await mountWithI18n(TheFooter);

    const contact = wrapper
      .findAll("nav.links a")
      .find((a) => a.attributes("href") === COPY.zh.ui.contact.href)!;
    expect(contact).toBeDefined();
    expect(contact.attributes("target")).toBe("_blank");
    expect(contact.attributes("rel")).toBe("noopener noreferrer");
  });

  it("版权年份取当年，不写死", async () => {
    const { wrapper } = await mountWithI18n(TheFooter);

    expect(wrapper.get(".legal").text()).toContain(String(new Date().getFullYear()));
  });
});
