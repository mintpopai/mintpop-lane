import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";
import { useAuthStore } from "./auth";

const adminUser: MeResponse = { id: 1, email: "a@b.c", name: "甲", role: "ADMIN", subscriptions: [] };
const memberUser: MeResponse = { id: 2, email: "m@b.c", name: "", role: "MEMBER", subscriptions: [] };

function fakeApi(result: MeResponse | Error): AuthApi {
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
    expect(await store.refreshAuthState(fakeApi(adminUser))).toBe(true);
    expect(store.authenticated).toBe(true);
    expect(store.isAdmin).toBe(true);
    expect(store.displayName).toBe("甲");
  });

  it("401 视为未登录且不抛异常", async () => {
    const store = useAuthStore();
    expect(await store.refreshAuthState(fakeApi(new UnauthorizedError()))).toBe(false);
    expect(store.authenticated).toBe(false);
    expect(store.isAdmin).toBe(false);
  });

  it("网络异常原样抛出，不误判成未登录", async () => {
    const store = useAuthStore();
    await expect(store.refreshAuthState(fakeApi(new Error("网络错误")))).rejects.toThrow("网络错误");
    expect(store.isAdmin).toBeNull();
  });

  it("signIn 先打环路标记再整页跳登录入口", () => {
    // jsdom 的 location.assign 是 not implemented，会抛错；换成可断言的桩
    const originalLocation = window.location;
    const assignMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { ...originalLocation, assign: assignMock },
      writable: true,
      configurable: true,
    });
    sessionStorage.clear();
    try {
      useAuthStore().signIn();

      expect(assignMock).toHaveBeenCalledWith("/oauth2/authorization/logto");
      // 标记在跳转之前落下，回来仍未登录时守卫才能熔断出「会话没生效」
      expect(sessionStorage.getItem("lane.loginRedirectAt")).not.toBeNull();
    } finally {
      Object.defineProperty(window, "location", {
        value: originalLocation,
        writable: true,
        configurable: true,
      });
    }
  });

  it("普通成员已登录但非管理员，姓名缺失回退邮箱", async () => {
    const store = useAuthStore();
    await store.refreshAuthState(fakeApi(memberUser));
    expect(store.isAdmin).toBe(false);
    expect(store.displayName).toBe("m@b.c");
  });
});
