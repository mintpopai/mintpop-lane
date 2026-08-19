import { beforeEach, describe, expect, it } from "vitest";
import { loadRuntimeConfig, resetRuntimeConfig, runtimeConfig } from "./runtime";

const 完整配置 = {
  logtoEndpoint: "https://tenant.logto.app",
  logtoAppId: "abc123",
  apiResource: "https://api.lane.mintpop.internal",
  apiBaseUrl: "/api",
};

function 假fetch(status: number, body: unknown): typeof fetch {
  return (async () =>
    ({
      ok: status >= 200 && status < 300,
      status,
      json: async () => body,
    }) as Response) as unknown as typeof fetch;
}

describe("loadRuntimeConfig", () => {
  beforeEach(() => {
    resetRuntimeConfig();
  });

  it("读到完整配置后可以用 runtimeConfig() 同步取回", async () => {
    const loaded = await loadRuntimeConfig(假fetch(200, 完整配置));

    expect(loaded).toEqual(完整配置);
    expect(runtimeConfig()).toEqual(完整配置);
  });

  it("取不到 config.json 时报出可操作的中文错误，而不是让页面白屏", async () => {
    await expect(loadRuntimeConfig(假fetch(404, null))).rejects.toThrow("/config.json");
  });

  it("缺字段时点名是哪一项缺——部署时漏填是最常见的事故", async () => {
    const 缺一项 = { ...完整配置, logtoAppId: "" };

    await expect(loadRuntimeConfig(假fetch(200, 缺一项))).rejects.toThrow("logtoAppId");
  });

  it("尚未加载就取用会立刻报错，避免拿到半初始化的客户端", () => {
    expect(() => runtimeConfig()).toThrow("尚未加载");
  });
});
