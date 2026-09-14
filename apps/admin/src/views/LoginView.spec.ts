import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import LoginView from "./LoginView.vue";

describe("LoginView", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("只给准入条件与一个登录动作，不罗列后台能管什么", () => {
    const wrapper = mount(LoginView);

    expect(wrapper.get(".gate-title").text()).toBe("统一登录");
    expect(wrapper.get(".gate-text").text()).toBe("请使用 管理员账号 登录");
    // 点名的是准入条件，加粗那段不能丢——丢了就只剩一句没有重点的话
    expect(wrapper.get(".gate-text strong").text()).toBe("管理员账号");
    expect(wrapper.get(".gate-btn").text()).toBe("MintPop 统一登录");
  });

  // 卡片的视觉主体，只有登录页有：排障页与无权限页靠正文撑高度，再加图标会撑过头
  it("卡里放产品图标当视觉主体，装饰图不带 alt 文案", () => {
    const mark = mount(LoginView).get(".gate-mark");

    expect(mark.attributes("src")).toContain("products/lane/lane-app-cloud.png");
    expect(mark.attributes("alt")).toBe("");
    expect(mark.attributes("width")).toBe("128");
    expect(mark.attributes("height")).toBe("128");
  });
});
