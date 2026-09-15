import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { defineComponent, h } from "vue";
import { DOWNLOADS_API, type DownloadsManifest } from "./release";

const MANIFEST: DownloadsManifest = {
  version: "0.4.2",
  pubDate: "2026-09-01T00:00:00Z",
  platforms: {
    "darwin-aarch64": { url: "https://dl.example/lane.dmg", size: 33_554_432 },
    "windows-x86_64": { url: "https://dl.example/lane.exe", size: 41_943_040 },
  },
};

const MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";

/**
 * 每次都换一份全新的模块实例。
 *
 * useRelease 刻意把拉取结果与 started 标志放在模块级（全站共享一次拉取），
 * 所以同一份模块在第二个用例里会直接跳过 fetch、还带着上一个用例的数据。
 * 这里用 resetModules + 动态 import 把它隔开——不这么做，除第一个以外的用例全是假绿。
 */
async function mountRelease(opts: { ua?: string; touch?: number; fetchImpl?: typeof fetch } = {}) {
  vi.resetModules();
  vi.stubGlobal("navigator", {
    userAgent: opts.ua ?? MAC_UA,
    maxTouchPoints: opts.touch ?? 0,
  });
  const fetchMock = vi.fn(
    opts.fetchImpl ?? (async () => new Response(JSON.stringify(MANIFEST), { status: 200 })),
  );
  vi.stubGlobal("fetch", fetchMock);

  const { useRelease } = await import("./useRelease");
  let api!: ReturnType<typeof useRelease>;
  const Probe = defineComponent({
    name: "ReleaseProbe",
    setup() {
      api = useRelease();
      return () => h("div");
    },
  });
  const wrapper = mount(Probe);
  await flushPromises();
  return { api, fetchMock, wrapper };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useRelease 拉取成功", () => {
  it("打的是同源反代端点，不直连 R2（免得给 R2 配 CORS）", async () => {
    const { fetchMock } = await mountRelease();

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][0]).toBe(DOWNLOADS_API);
  });

  it("版本号与各平台直链就位", async () => {
    const { api } = await mountRelease();

    expect(api.version.value).toBe("0.4.2");
    expect(api.platformFor("MAC_ARM")).toEqual({
      url: "https://dl.example/lane.dmg",
      size: 33_554_432,
    });
    expect(api.platformFor("WINDOWS")?.url).toBe("https://dl.example/lane.exe");
  });

  it("主按钮按访客系统选平台：mac 给 Apple 芯片包", async () => {
    const { api } = await mountRelease({ ua: MAC_UA });

    expect(api.os.value).toBe("MAC");
    expect(api.primaryKey.value).toBe("MAC_ARM");
  });

  it("windows 访客给 exe", async () => {
    const { api } = await mountRelease({ ua: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" });

    expect(api.primaryKey.value).toBe("WINDOWS");
  });

  it("认不出的系统没有主按钮，交给调用方兜底到下载区", async () => {
    const { api } = await mountRelease({ ua: "Mozilla/5.0 (X11; Linux x86_64)" });

    expect(api.os.value).toBe("OTHER");
    expect(api.primaryKey.value).toBeNull();
  });

  it("全站只拉一次：第二个组件复用同一份结果，不再发请求", async () => {
    const { fetchMock } = await mountRelease();
    expect(fetchMock).toHaveBeenCalledOnce();

    // 同一份模块实例里再挂一个用到它的组件
    const { useRelease } = await import("./useRelease");
    mount(defineComponent({ setup: () => (useRelease(), () => h("div")) }));
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe("useRelease 拿不到清单", () => {
  it("上游返 5xx 时不显示版本号、各平台都给 null", async () => {
    const { api } = await mountRelease({
      fetchImpl: async () => new Response("boom", { status: 503 }),
    });

    expect(api.version.value).toBeNull();
    expect(api.platformFor("MAC_ARM")).toBeNull();
    expect(api.platformFor("WINDOWS")).toBeNull();
  });

  it("网络整个断掉也只是降级，不把异常抛给页面", async () => {
    const { api } = await mountRelease({
      fetchImpl: async () => {
        throw new TypeError("Failed to fetch");
      },
    });

    expect(api.version.value).toBeNull();
    expect(api.platformFor("WINDOWS")).toBeNull();
  });

  it("清单形状不对时整体作废，绝不让 undefined 漏进 href", async () => {
    const { api } = await mountRelease({
      fetchImpl: async () => new Response(JSON.stringify({ nope: true }), { status: 200 }),
    });

    expect(api.version.value).toBeNull();
    expect(api.platformFor("MAC_ARM")).toBeNull();
  });

  it("即便拿不到清单，系统识别照常工作——主按钮仍能兜底到下载区", async () => {
    const { api } = await mountRelease({
      ua: "Mozilla/5.0 (Windows NT 10.0)",
      fetchImpl: async () => new Response("", { status: 404 }),
    });

    expect(api.primaryKey.value).toBe("WINDOWS");
    expect(api.platformFor("WINDOWS")).toBeNull();
  });
});
