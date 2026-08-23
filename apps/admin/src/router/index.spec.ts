import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory } from "vue-router";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";
import { markLoginRedirect } from "../utils/loginLoop";
import { createAppRouter } from "./index";

const adminUser: MeResponse = { id: 1, email: "a@b.c", role: "ADMIN", subscriptions: [] };
const memberUser: MeResponse = { id: 2, email: "m@b.c", role: "MEMBER", subscriptions: [] };

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
  // jsdom 的 location.assign 是 not implemented，会抛错；换成可断言的桩
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
  it("管理员放行进目标页", async () => {
    const router = createRouter(adminUser);

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("USERS");
  });

  it("已登录但不是管理员落到无权限页", async () => {
    const router = createRouter(memberUser);

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("FORBIDDEN");
  });

  it("未登录（401）时落到登录落地页，不自动跳 Logto", async () => {
    const router = createRouter(new UnauthorizedError());

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("LOGIN");
    expect(assignMock).not.toHaveBeenCalled();
    // 只有用户点「登录」才打环路标记，落地页本身不算一次登录尝试
    expect(sessionStorage.getItem("lane.loginRedirectAt")).toBeNull();
  });

  it("刚跳过登录又回到未登录：熔断到登录失败页，不落地页循环", async () => {
    markLoginRedirect(Date.now());
    const router = createRouter(new UnauthorizedError());

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("LOGIN_ERROR");
    expect(assignMock).not.toHaveBeenCalled();
  });

  it("探测抛普通异常时放行，不把人赶去登录页或无权限页", async () => {
    const router = createRouter(new Error("网络错误"));

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("USERS");
    expect(assignMock).not.toHaveBeenCalled();
  });

  it("服务端带 ?login_error=1 回来时直接落登录失败页", async () => {
    const router = createRouter(adminUser);

    await router.push("/users?login_error=1");

    expect(router.currentRoute.value.name).toBe("LOGIN_ERROR");
  });

  it("登录成功会清掉环路标记，避免下次登录被误判", async () => {
    markLoginRedirect(Date.now());
    const router = createRouter(adminUser);

    await router.push("/users");

    expect(sessionStorage.getItem("lane.loginRedirectAt")).toBeNull();
  });
});
