import { describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import VerifySection from "./VerifySection.vue";

describe("VerifySection", () => {
  it("左右各一张卡：对照组与 Lane，构成这一节的论据", async () => {
    const { wrapper } = await mountWithI18n(VerifySection);

    expect(wrapper.findAll(".pair .card")).toHaveLength(2);
    expect(wrapper.get(".card.plain .code").text()).toBe(COPY.zh.verify.others.code);
    expect(wrapper.get(".card.ours .label").text()).toBe(COPY.zh.verify.lane.label);
  });

  it("「重试」只是画成按钮的样子，不能真的点", async () => {
    const { wrapper } = await mountWithI18n(VerifySection);

    const retry = wrapper.get(".card.ours .retry");
    expect(retry.element.tagName).toBe("SPAN");
    expect(wrapper.find(".card.ours button").exists()).toBe(false);
  });

  it("英文页两张卡一起换英文", async () => {
    const { wrapper } = await mountWithI18n(VerifySection, { locale: "en" });

    expect(wrapper.get(".card.plain .note").text()).toBe(COPY.en.verify.others.note);
    expect(wrapper.get(".card.ours .detail").text()).toBe(COPY.en.verify.lane.detail);
  });
});
