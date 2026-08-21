import { beforeEach, describe, expect, it } from "vitest";
import { loadRuntimeConfig, resetRuntimeConfig, runtimeConfig } from "./runtime";

const fullConfig = {
  apiBaseUrl: "/api",
};

function fakeFetch(status: number, body: unknown): typeof fetch {
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
    const loaded = await loadRuntimeConfig(fakeFetch(200, fullConfig));

    expect(loaded).toEqual(fullConfig);
    expect(runtimeConfig()).toEqual(fullConfig);
  });

  it("取不到 config.json 时报出可操作的中文错误，而不是让页面白屏", async () => {
    await expect(loadRuntimeConfig(fakeFetch(404, null))).rejects.toThrow("/config.json");
  });

  it("缺字段时点名是哪一项缺——部署时漏填是最常见的事故", async () => {
    const missingOne = { ...fullConfig, apiBaseUrl: "" };

    await expect(loadRuntimeConfig(fakeFetch(200, missingOne))).rejects.toThrow("apiBaseUrl");
  });

  it("尚未加载就取用会立刻报错，避免拿到半初始化的客户端", () => {
    expect(() => runtimeConfig()).toThrow("尚未加载");
  });
});
