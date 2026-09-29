import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import SubscriptionQuota from "./SubscriptionQuota.vue";

describe("SubscriptionQuota", () => {
  it("有额度时画进度条并写百分比", () => {
    const wrapper = mount(SubscriptionQuota, {
      props: { subscription: { usedBytes: 64, totalBytes: 100 } },
    });

    expect(wrapper.get(".quota-pct").text()).toBe("64%");
    expect(wrapper.get(".quota-bar-fill").attributes("style")).toContain("width: 64%");
    expect(wrapper.find(".quota-none").exists()).toBe(false);
  });

  it("没额度头时写「未提供」，绝不显示成 0%", () => {
    const wrapper = mount(SubscriptionQuota, {
      props: { subscription: { usedBytes: null, totalBytes: null } },
    });

    expect(wrapper.get(".quota-none").text()).toBe("未提供");
    expect(wrapper.text()).not.toContain("0%");
  });
});
