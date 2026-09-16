import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AppLayout from "./AppLayout.vue";
import { useAuthStore } from "../stores/auth";
import type { MeResponse } from "../api/types";

const ME: MeResponse = { id: 1, email: "buyer@mintpop.ai", role: "MEMBER", subscriptions: [] };

/** 挂真 DOM：Esc 与「点别处收起」走的是文档级监听，脱离 document 收不到事件 */
function mountLayout() {
  useAuthStore().me = ME;
  return mount(AppLayout, {
    attachTo: document.body,
    global: {
      stubs: { RouterLink: { template: "<a><slot /></a>" }, RouterView: { template: "<div />" } },
    },
  });
}

describe("AppLayout", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("顶栏：品牌行读作「Lane 控制台」，三个入口按用户动线排：我的订阅 → 购买套餐 → 我的订单", () => {
    const wrapper = mountLayout();
    expect(wrapper.get(".bar-brand-text").text().replace(/\s+/g, " ")).toBe("Lane 控制台");
    expect(wrapper.findAll(".bar-link").map((l) => l.text())).toEqual([
      "我的订阅",
      "购买套餐",
      "我的订单",
    ]);
    wrapper.unmount();
  });

  it("用户菜单默认收起，点胶囊才展开，菜单里给出登录邮箱与退出登录", async () => {
    const wrapper = mountLayout();
    expect(wrapper.find(".bar-menu").exists()).toBe(false);

    await wrapper.get(".bar-user").trigger("click");

    expect(wrapper.get(".bar-menu").text()).toContain("buyer@mintpop.ai");
    expect(wrapper.get(".bar-signout").text()).toBe("退出登录");
    wrapper.unmount();
  });

  it("按 Esc 收起已展开的用户菜单", async () => {
    const wrapper = mountLayout();
    await wrapper.get(".bar-user").trigger("click");

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await wrapper.vm.$nextTick();

    expect(wrapper.find(".bar-menu").exists()).toBe(false);
    wrapper.unmount();
  });

  it("点菜单以外的地方收起用户菜单", async () => {
    const wrapper = mountLayout();
    await wrapper.get(".bar-user").trigger("click");

    document.body.dispatchEvent(new Event("pointerdown", { bubbles: true }));
    await wrapper.vm.$nextTick();

    expect(wrapper.find(".bar-menu").exists()).toBe(false);
    wrapper.unmount();
  });

  it("点菜单里的退出登录即登出", async () => {
    const wrapper = mountLayout();
    const signOut = vi.spyOn(useAuthStore(), "signOut").mockImplementation(() => {});
    await wrapper.get(".bar-user").trigger("click");

    await wrapper.get(".bar-signout").trigger("click");

    expect(signOut).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
});
