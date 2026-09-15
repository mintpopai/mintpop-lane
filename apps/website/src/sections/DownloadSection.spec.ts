import { beforeEach, describe, expect, it, vi } from "vitest";
import { COPY } from "../content/copy";
import type { ManifestPlatform, MatchKey } from "../composables/release";
import { mountWithI18n } from "../testing";
import DownloadSection from "./DownloadSection.vue";

// 清单拉取本身在 useRelease.spec.ts 里测；这里只关心「拿到/拿不到清单时页面长什么样」，
// 故把 useRelease 换成一张可随用例改写的桌子，免得再去摆弄 fetch 与模块级缓存。
const release = vi.hoisted(() => ({
  version: null as string | null,
  platforms: {} as Partial<Record<MatchKey, ManifestPlatform>>,
}));

vi.mock("../composables/useRelease", async () => {
  const { computed } = await import("vue");
  return {
    useRelease: () => ({
      version: computed(() => release.version),
      platformFor: (key: MatchKey) => release.platforms[key] ?? null,
    }),
  };
});

beforeEach(() => {
  release.version = null;
  release.platforms = {};
});

describe("DownloadSection 拿到清单", () => {
  beforeEach(() => {
    release.version = "0.4.2";
    release.platforms = {
      MAC_ARM: { url: "https://dl.example/lane.dmg", size: 33_554_432 },
      WINDOWS: { url: "https://dl.example/lane.exe", size: 41_943_040 },
    };
  });

  it("两个平台各给一条直链，不是拿不到时那种空壳", async () => {
    const { wrapper } = await mountWithI18n(DownloadSection);

    const cards = wrapper.findAll(".platform");
    expect(cards.map((c) => c.attributes("href"))).toEqual([
      "https://dl.example/lane.dmg",
      "https://dl.example/lane.exe",
    ]);
    expect(cards.every((c) => !c.classes().includes("is-unavailable"))).toBe(true);
  });

  it("版本号带 v 前缀显示（清单里存的是裸版本号）", async () => {
    const { wrapper } = await mountWithI18n(DownloadSection);

    expect(wrapper.get(".version").text()).toContain("v0.4.2");
    expect(wrapper.get(".version").text()).toContain(COPY.zh.ui.download.latest);
  });

  it("体积按当前语言拼前缀", async () => {
    const zh = await mountWithI18n(DownloadSection);
    expect(zh.wrapper.findAll(".file")[0].text()).toContain("约 32 MB");

    const en = await mountWithI18n(DownloadSection, { locale: "en" });
    expect(en.wrapper.findAll(".file")[0].text()).toContain("~32 MB");
  });

  it("有直链时不出「暂无下载」那句", async () => {
    const { wrapper } = await mountWithI18n(DownloadSection);

    expect(wrapper.find(".unavailable").exists()).toBe(false);
  });
});

describe("DownloadSection 拿不到清单", () => {
  it("两张卡都转不可用态，且不带 href——空 href 会跳回当前页", async () => {
    const { wrapper } = await mountWithI18n(DownloadSection);

    const cards = wrapper.findAll(".platform");
    expect(cards.every((c) => c.classes().includes("is-unavailable"))).toBe(true);
    expect(cards.every((c) => c.attributes("href") === undefined)).toBe(true);
  });

  it("如实说明当前状态，而不是假装在加载", async () => {
    const { wrapper } = await mountWithI18n(DownloadSection);

    expect(wrapper.get(".version").text()).toBe(COPY.zh.ui.download.loading);
    expect(wrapper.get(".unavailable").text()).toBe(COPY.zh.download.unavailable);
  });

  it("体积拿不到就整段不渲染，不出「约 0 MB」", async () => {
    release.platforms = { MAC_ARM: { url: "https://dl.example/lane.dmg", size: 0 } };
    const { wrapper } = await mountWithI18n(DownloadSection);

    const macFile = wrapper.findAll(".file")[0].text();
    expect(macFile).toContain(".dmg");
    expect(macFile).not.toContain("MB");
    expect(macFile).not.toContain("0");
  });

  it("只有一个平台缺失时，另一个照常可下", async () => {
    release.platforms = { WINDOWS: { url: "https://dl.example/lane.exe", size: 41_943_040 } };
    const { wrapper } = await mountWithI18n(DownloadSection);

    const cards = wrapper.findAll(".platform");
    expect(cards[0].classes()).toContain("is-unavailable");
    expect(cards[1].attributes("href")).toBe("https://dl.example/lane.exe");
    // 还有一个平台可下，就不该说「都没得下」
    expect(wrapper.find(".unavailable").exists()).toBe(false);
  });
});

describe("DownloadSection 系统要求", () => {
  it("每条平台要求排成 dt/dd 一一对应", async () => {
    const { wrapper } = await mountWithI18n(DownloadSection);

    const dts = wrapper.findAll(".req dt");
    expect(dts.map((d) => d.text())).toEqual(COPY.zh.download.requirements.map((r) => r.platform));
    expect(wrapper.findAll(".req dd")).toHaveLength(dts.length);
  });
});
