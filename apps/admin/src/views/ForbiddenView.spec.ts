import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { useAuthStore } from "../stores/auth";
import ForbiddenView from "./ForbiddenView.vue";

describe("ForbiddenView", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("点名当前登录的邮箱，并给出退出登录与返回官网两个出口", () => {
    const auth = useAuthStore();
    auth.me = { id: 2, email: "m@b.c", role: "MEMBER", subscriptions: [] };
    const wrapper = mount(ForbiddenView);

    expect(wrapper.get(".gate-text .fact").text()).toBe("m@b.c");
    expect(wrapper.get(".gate-btn").text()).toBe("退出登录");
    expect(wrapper.get(".gate-link").attributes("href")).toBe("https://lane.mintpop.ai");
  });

  it("拿不到邮箱时不点名，避免出现「当前登录的是 。」这种断句", () => {
    const wrapper = mount(ForbiddenView);

    expect(wrapper.find(".gate-text .fact").exists()).toBe(false);
    expect(wrapper.get(".gate-text").text()).toBe("找系统管理员把这个账号设为管理员，或换个账号登录。");
  });
});
