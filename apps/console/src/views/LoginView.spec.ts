import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import LoginView from "./LoginView.vue";

describe("LoginView", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("只讲用哪种账号登录与一个登录动作：控制台不限角色，说明里不出现「管理员」", () => {
    const wrapper = mount(LoginView);
    expect(wrapper.get(".gate-title").text()).toBe("统一登录");
    expect(wrapper.get(".gate-text").text()).toBe("请使用你的 MintPop 账号 登录");
    expect(wrapper.get(".gate-text strong").text()).toBe("MintPop 账号");
    expect(wrapper.get(".gate-text").text()).not.toContain("管理员");
    expect(wrapper.get(".gate-btn").text()).toBe("MintPop 统一登录");
  });

  it("卡里放产品图标当视觉主体，装饰图不带 alt 文案", () => {
    const mark = mount(LoginView).get(".gate-mark");
    expect(mark.attributes("src")).toContain("products/lane/lane-app-cloud.png");
    expect(mark.attributes("alt")).toBe("");
    expect(mark.attributes("width")).toBe("128");
  });
});
