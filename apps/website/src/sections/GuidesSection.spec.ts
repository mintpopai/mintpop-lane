import { describe, expect, it } from "vitest";
import { GUIDES } from "../content/guides";
import { mountWithI18n } from "../testing";
import GuidesSection from "./GuidesSection.vue";

describe("GuidesSection", () => {
  it("四篇指南全部列出，条目与注册表同源、顺序一致", async () => {
    const { wrapper } = await mountWithI18n(GuidesSection);

    const cards = wrapper.findAll(".grid li");
    expect(cards).toHaveLength(GUIDES.length);
    expect(cards.map((c) => c.get("h3").text())).toEqual(GUIDES.map((g) => g.copy.zh.h1));
  });

  it("英文页的内链带 /en 前缀，标题也换成英文", async () => {
    const { wrapper } = await mountWithI18n(GuidesSection, { locale: "en" });

    const links = wrapper.findAll(".grid a");
    expect(links.map((a) => a.attributes("href"))).toEqual(
      GUIDES.map((g) => `/en/guides/${g.slug}/`),
    );
    expect(links[0].get("h3").text()).toBe(GUIDES[0].copy.en.h1);
  });
});
