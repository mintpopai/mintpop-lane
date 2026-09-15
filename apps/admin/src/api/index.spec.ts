import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { loginPagePath } from "../auth/constants";
import type { HttpClient, HttpClientOptions } from "./http";

// 组装层的职责就三件：前缀钉死 /api、两个 api 共用同一个 http、401 落回登录落地页。
// 这里把 createHttpClient 换成一个只记录入参的假货，好把这三件事看清楚。
const created: HttpClientOptions[] = [];

vi.mock("./http", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./http")>();
  return {
    ...actual,
    createHttpClient: (options: HttpClientOptions): HttpClient => {
      created.push(options);
      return {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn(),
      } as unknown as HttpClient;
    },
  };
});

/** 组装层的实例是模块级缓存，每个用例都得换一份新模块，否则测的是上一个用例留下的那份 */
async function freshModule() {
  vi.resetModules();
  created.length = 0;
  return import("./index");
}

beforeEach(() => {
  created.length = 0;
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api 组装层", () => {
  it("接口前缀钉死 /api：它由服务端路由写死，没有换环境要换值的场景", async () => {
    const { adminApi } = await freshModule();
    adminApi();

    expect(created).toHaveLength(1);
    expect(created[0].baseUrl).toBe("/api");
  });

  it("同一个 api 反复取用时复用同一实例，不每次新建一份", async () => {
    const { adminApi, authApi } = await freshModule();

    expect(adminApi()).toBe(adminApi());
    expect(authApi()).toBe(authApi());
  });

  it("两个 api 共用同一个 http 客户端，只建一次", async () => {
    const { adminApi, authApi } = await freshModule();

    adminApi();
    authApi();

    expect(created).toHaveLength(1);
  });

  it("会话失效时整页落回登录落地页，不静默跳 Logto——由用户自己点登录", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
    const { adminApi } = await freshModule();
    adminApi();

    created[0].onUnauthorized?.();

    expect(assign).toHaveBeenCalledWith(loginPagePath);
  });
});
