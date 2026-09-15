import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import LoginErrorView from "./LoginErrorView.vue";

describe("LoginErrorView", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("说明会话没生效的常见原因，并给「重试登录」一个动作；文案说的是控制台不是管理后台", () => {
    const wrapper = mount(LoginErrorView);
    expect(wrapper.get(".gate-title").text()).toBe("登录未能完成");
    expect(wrapper.text()).toContain("控制台");
    expect(wrapper.text()).not.toContain("管理后台");
    expect(wrapper.get(".gate-btn").text()).toBe("重试登录");
    expect(wrapper.get(".gate-box").classes()).toContain("gate-box-wide");
  });
});
