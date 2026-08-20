import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";
import { useAuthStore } from "./auth";

const 管理员: MeResponse = { id: 1, email: "a@b.c", name: "甲", role: "ADMIN", subscriptions: [] };
const 普通成员: MeResponse = { id: 2, email: "m@b.c", name: "", role: "MEMBER", subscriptions: [] };

function 假api(result: MeResponse | Error): AuthApi {
  return {
    me: async () => {
      if (result instanceof Error) throw result;
      return result;
    },
  };
}

describe("useAuthStore", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("me 成功即已登录，管理员身份由 role 得出", async () => {
    const store = useAuthStore();
    expect(store.isAdmin).toBeNull();
    expect(await store.refreshAuthState(假api(管理员))).toBe(true);
    expect(store.authenticated).toBe(true);
    expect(store.isAdmin).toBe(true);
    expect(store.displayName).toBe("甲");
  });

  it("401 视为未登录且不抛异常", async () => {
    const store = useAuthStore();
    expect(await store.refreshAuthState(假api(new UnauthorizedError()))).toBe(false);
    expect(store.authenticated).toBe(false);
    expect(store.isAdmin).toBe(false);
  });

  it("网络异常原样抛出，不误判成未登录", async () => {
    const store = useAuthStore();
    await expect(store.refreshAuthState(假api(new Error("网络错误")))).rejects.toThrow("网络错误");
    expect(store.isAdmin).toBeNull();
  });

  it("signIn 先打环路标记再整页跳登录入口", () => {
    // jsdom 的 location.assign 是 not implemented，会抛错；换成可断言的桩
    const 原始location = window.location;
    const assign跳转 = vi.fn();
    Object.defineProperty(window, "location", {
      value: { ...原始location, assign: assign跳转 },
      writable: true,
      configurable: true,
    });
    sessionStorage.clear();
    try {
      useAuthStore().signIn();

      expect(assign跳转).toHaveBeenCalledWith("/oauth2/authorization/logto");
      // 标记在跳转之前落下，回来仍未登录时守卫才能熔断出「会话没生效」
      expect(sessionStorage.getItem("lane.loginRedirectAt")).not.toBeNull();
    } finally {
      Object.defineProperty(window, "location", {
        value: 原始location,
        writable: true,
        configurable: true,
      });
    }
  });

  it("普通成员已登录但非管理员，姓名缺失回退邮箱", async () => {
    const store = useAuthStore();
    await store.refreshAuthState(假api(普通成员));
    expect(store.isAdmin).toBe(false);
    expect(store.displayName).toBe("m@b.c");
  });
});
