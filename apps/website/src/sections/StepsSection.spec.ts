import { describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import StepsSection from "./StepsSection.vue";

describe("StepsSection", () => {
  it("三步用有序列表排，顺序本身是内容的一部分", async () => {
    const { wrapper } = await mountWithI18n(StepsSection);

    expect(wrapper.find("ol.list").exists()).toBe(true);
    const items = wrapper.findAll("ol.list li");
    expect(items.map((li) => li.get("h3").text())).toEqual(COPY.zh.steps.items.map((s) => s.title));
  });

  it("英文页换英文文案，条数与中文一致", async () => {
    const { wrapper } = await mountWithI18n(StepsSection, { locale: "en" });

    const items = wrapper.findAll("ol.list li");
    expect(items).toHaveLength(COPY.zh.steps.items.length);
    expect(items[0].get("h3").text()).toBe(COPY.en.steps.items[0].title);
  });
});
