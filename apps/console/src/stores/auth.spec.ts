import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";
import { useAuthStore } from "./auth";

const member: MeResponse = { id: 2, email: "m@b.c", role: "MEMBER", subscriptions: [] };

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

  it("me 成功即已登录；控制台不区分角色，普通成员与管理员一视同仁", async () => {
    const store = useAuthStore();
    expect(store.authenticated).toBe(false);
    expect(await store.refreshAuthState(fakeApi(member))).toBe(true);
    expect(store.authenticated).toBe(true);
    expect(store.email).toBe("m@b.c");
    expect(store.me?.subscriptions).toEqual([]);
  });

  it("401 视为未登录且不抛异常", async () => {
    const store = useAuthStore();
    expect(await store.refreshAuthState(fakeApi(new UnauthorizedError()))).toBe(false);
    expect(store.authenticated).toBe(false);
  });

  it("网络异常原样抛出，不误判成未登录", async () => {
    const store = useAuthStore();
    await expect(store.refreshAuthState(fakeApi(new Error("网络错误")))).rejects.toThrow(
      "网络错误",
    );
  });

  it("signIn 先打控制台自己的环路标记再整页跳登录入口", () => {
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
      expect(sessionStorage.getItem("lane.console.loginRedirectAt")).not.toBeNull();
    } finally {
      Object.defineProperty(window, "location", {
        value: originalLocation,
        writable: true,
        configurable: true,
      });
    }
  });
});
