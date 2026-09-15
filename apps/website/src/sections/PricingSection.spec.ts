import type { DOMWrapper } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import PricingSection from "./PricingSection.vue";

describe("PricingSection", () => {
  it("两档并列，只有最后一档加重成主推档", async () => {
    const { wrapper } = await mountWithI18n(PricingSection);

    const plans = wrapper.findAll(".plan");
    expect(plans).toHaveLength(COPY.zh.pricing.plans.length);
    expect(plans.at(-1)!.classes()).toContain("featured");
    expect(plans[0].classes()).not.toContain("featured");
  });

  it("两张卡行序完全一致：同一行横着看就是同一项在两档下的取值", async () => {
    const { wrapper } = await mountWithI18n(PricingSection);

    const rowsOf = (plan: DOMWrapper<Element>) =>
      [".name", ".badge", ".amount", ".was", ".quota-label", ".quota", ".fit"].map((sel) =>
        plan.find(sel).exists(),
      );
    const plans = wrapper.findAll(".plan");
    expect(rowsOf(plans[0])).toEqual(rowsOf(plans[1]));
    expect(rowsOf(plans[0]).every(Boolean)).toBe(true);
  });

  it("原价用 <s> 而非纯样式划线，读屏也能知道这个价已作废", async () => {
    const { wrapper } = await mountWithI18n(PricingSection);

    const was = wrapper.findAll(".plan").map((p) => p.get(".was"));
    expect(was.map((w) => w.element.tagName)).toEqual(["S", "S"]);
    expect(was.map((w) => w.text())).toEqual(COPY.zh.pricing.plans.map((p) => p.was));
  });

  it("共同保障抽出来排成 dl，每条 term 配一条 body", async () => {
    const { wrapper } = await mountWithI18n(PricingSection);

    const terms = wrapper.findAll(".assurances dt");
    const bodies = wrapper.findAll(".assurances dd");
    expect(terms).toHaveLength(COPY.zh.pricing.assurances.length);
    expect(bodies).toHaveLength(terms.length);
    expect(terms.map((t) => t.text())).toEqual(COPY.zh.pricing.assurances.map((a) => a.term));
  });

  it("英文页整段换英文文案", async () => {
    const { wrapper } = await mountWithI18n(PricingSection, { locale: "en" });

    expect(wrapper.get(".section-title").text()).toBe(COPY.en.pricing.title);
    expect(wrapper.findAll(".plan .name").map((n) => n.text())).toEqual(
      COPY.en.pricing.plans.map((p) => p.name),
    );
  });
});
