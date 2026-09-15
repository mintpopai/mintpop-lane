import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory } from "vue-router";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";
import { markLoginRedirect } from "../utils/loginLoop";
import { createAppRouter } from "./index";

const member: MeResponse = { id: 2, email: "m@b.c", role: "MEMBER", subscriptions: [] };

function fakeApi(result: MeResponse | Error): AuthApi {
  return {
    me: async () => {
      if (result instanceof Error) throw result;
      return result;
    },
  };
}

function createRouter(result: MeResponse | Error) {
  return createAppRouter(fakeApi(result), createMemoryHistory());
}

let originalLocation: Location;
let assignMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  setActivePinia(createPinia());
  sessionStorage.clear();
  originalLocation = window.location;
  assignMock = vi.fn();
  Object.defineProperty(window, "location", {
    value: { ...originalLocation, assign: assignMock },
    writable: true,
    configurable: true,
  });
});

afterEach(() => {
  Object.defineProperty(window, "location", {
    value: originalLocation,
    writable: true,
    configurable: true,
  });
});

describe("路由守卫", () => {
  it("已登录即放行，不看角色：普通成员进我的订阅", async () => {
    const router = createRouter(member);
    await router.push("/");
    expect(router.currentRoute.value.name).toBe("SUBSCRIPTIONS");
  });

  it("管理员账号同样能用控制台", async () => {
    const router = createRouter({ ...member, role: "ADMIN" });
    await router.push("/plans");
    expect(router.currentRoute.value.name).toBe("PLANS");
  });

  it("未登录（401）落到登录落地页，不自动跳 Logto", async () => {
    const router = createRouter(new UnauthorizedError());
    await router.push("/orders");
    expect(router.currentRoute.value.name).toBe("LOGIN");
    expect(assignMock).not.toHaveBeenCalled();
  });

  it("刚跳过登录又回到未登录：熔断到登录失败页", async () => {
    markLoginRedirect();
    const router = createRouter(new UnauthorizedError());
    await router.push("/");
    expect(router.currentRoute.value.name).toBe("LOGIN_ERROR");
  });

  it("服务端握手失败带 login_error=1 回来时直接进登录失败页", async () => {
    const router = createRouter(member);
    await router.push("/?login_error=1");
    expect(router.currentRoute.value.name).toBe("LOGIN_ERROR");
  });

  it("未知路径回到我的订阅", async () => {
    const router = createRouter(member);
    await router.push("/nope");
    expect(router.currentRoute.value.name).toBe("SUBSCRIPTIONS");
  });
});
