import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import AppLayout from "./AppLayout.vue";

describe("AppLayout", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("导航轨：品牌行读作「Lane 控制台」，三个入口按用户动线排：我的订阅 → 购买套餐 → 我的订单", () => {
    const wrapper = mount(AppLayout, {
      global: {
        stubs: { RouterLink: { template: "<a><slot /></a>" }, RouterView: { template: "<div />" } },
      },
    });
    expect(wrapper.get(".rail-brand-text").text().replace(/\s+/g, " ")).toBe("Lane 控制台");
    expect(wrapper.findAll(".rail-link").map((l) => l.text())).toEqual([
      "我的订阅",
      "购买套餐",
      "我的订单",
    ]);
    expect(wrapper.get(".rail-signout").text()).toBe("退出登录");
  });
});
