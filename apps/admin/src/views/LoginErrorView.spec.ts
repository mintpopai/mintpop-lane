import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuthStore } from "../stores/auth";
import LoginErrorView from "./LoginErrorView.vue";

describe("LoginErrorView", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("说清这是「跳转成环」而不是泛泛的登录失败，并列出常见原因", () => {
    const wrapper = mount(LoginErrorView);

    expect(wrapper.get(".gate-title").text()).toBe("登录未能完成");
    const body = wrapper.text();
    expect(body).toContain("来回跳转");
    // 三个常见原因都得在，少一个就等于让人自己猜
    expect(body).toContain("Logto");
    expect(body).toContain("会话签名密钥");
    expect(body).toContain("Cookie");
  });

  it("排障正文长，用宽卡排版，免得中文被断得很碎", () => {
    const wrapper = mount(LoginErrorView);

    expect(wrapper.findAll(".gate-text-long").length).toBeGreaterThan(0);
  });

  it("重试走 signIn：它会重打环路标记，再失败时守卫据此直接回本页", async () => {
    const auth = useAuthStore();
    const signIn = vi.spyOn(auth, "signIn").mockImplementation(() => {});
    const wrapper = mount(LoginErrorView);

    await wrapper.get(".gate-btn").trigger("click");

    expect(signIn).toHaveBeenCalledOnce();
  });

  it("只给「重试」这一个动作，不在排障页塞别的出口", () => {
    const wrapper = mount(LoginErrorView);

    const buttons = wrapper.findAll(".gate-actions button");
    expect(buttons).toHaveLength(1);
    expect(buttons[0].text()).toBe("重试登录");
  });
});
