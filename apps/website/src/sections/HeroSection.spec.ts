import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ManifestPlatform, MatchKey } from "../composables/release";
import { COPY } from "../content/copy";
import LaneVisual from "../components/LaneVisual.vue";
import { mountWithI18n } from "../testing";
import HeroSection from "./HeroSection.vue";

// 同 DownloadSection：清单拉取在 useRelease.spec.ts 里测，这里只看主按钮怎么随之变化
const release = vi.hoisted(() => ({
  version: null as string | null,
  primaryKey: null as MatchKey | null,
  platforms: {} as Partial<Record<MatchKey, ManifestPlatform>>,
}));

vi.mock("../composables/useRelease", async () => {
  const { computed } = await import("vue");
  return {
    useRelease: () => ({
      version: computed(() => release.version),
      primaryKey: computed(() => release.primaryKey),
      platformFor: (key: MatchKey) => release.platforms[key] ?? null,
    }),
  };
});

beforeEach(() => {
  release.version = null;
  release.primaryKey = null;
  release.platforms = {};
});

describe("HeroSection 主按钮", () => {
  it("mac 访客直接给 dmg 直链，按钮上写明是哪个平台", async () => {
    release.primaryKey = "MAC_ARM";
    release.platforms = { MAC_ARM: { url: "https://dl.example/lane.dmg", size: 1 } };
    const { wrapper } = await mountWithI18n(HeroSection);

    const primary = wrapper.get(".cta .btn-primary");
    expect(primary.attributes("href")).toBe("https://dl.example/lane.dmg");
    expect(primary.text()).toBe(COPY.zh.ui.hero.primaryMac);
  });

  it("windows 访客给 exe 直链", async () => {
    release.primaryKey = "WINDOWS";
    release.platforms = { WINDOWS: { url: "https://dl.example/lane.exe", size: 1 } };
    const { wrapper } = await mountWithI18n(HeroSection);

    const primary = wrapper.get(".cta .btn-primary");
    expect(primary.attributes("href")).toBe("https://dl.example/lane.exe");
    expect(primary.text()).toBe(COPY.zh.ui.hero.primaryWin);
  });

  it("认不出系统时兜底到下载区锚点，那里会如实说明状态", async () => {
    const { wrapper } = await mountWithI18n(HeroSection);

    const primary = wrapper.get(".cta .btn-primary");
    expect(primary.attributes("href")).toBe("#download");
    expect(primary.text()).toBe(COPY.zh.ui.hero.primaryFallback);
  });

  it("认得出系统但清单没到手，同样兜底到下载区而不是给个死链", async () => {
    release.primaryKey = "MAC_ARM";
    const { wrapper } = await mountWithI18n(HeroSection);

    expect(wrapper.get(".cta .btn-primary").attributes("href")).toBe("#download");
    expect(wrapper.get(".cta .btn-primary").text()).toBe(COPY.zh.ui.hero.primaryFallback);
  });

  it("次按钮恒指向下载区，任何系统都有路可走", async () => {
    const { wrapper } = await mountWithI18n(HeroSection);

    const ghost = wrapper.get(".cta .btn-ghost");
    expect(ghost.attributes("href")).toBe("#download");
    expect(ghost.text()).toBe(COPY.zh.ui.hero.allDownloads);
  });
});

describe("HeroSection 首屏内容", () => {
  it("拿到版本号才在注脚里显示它", async () => {
    const without = await mountWithI18n(HeroSection);
    expect(without.wrapper.get(".note").text()).not.toContain("v");
    expect(without.wrapper.get(".note").text()).toBe(COPY.zh.hero.note);

    release.version = "0.4.2";
    const withVersion = await mountWithI18n(HeroSection);
    expect(withVersion.wrapper.get(".note").text()).toContain("v0.4.2");
  });

  it("标题与引言各自断成两句，断句是语义的、不靠自动换行", async () => {
    const { wrapper } = await mountWithI18n(HeroSection);

    expect(wrapper.get("h1").text()).toContain(COPY.zh.hero.title[0]);
    expect(wrapper.get("h1 .accent").text()).toBe(COPY.zh.hero.title[1]);
    expect(wrapper.find("h1 br").exists()).toBe(true);
    expect(wrapper.get(".lede").text()).toContain(COPY.zh.hero.lede[1]);
  });

  it("首屏右侧是产品窗口示意", async () => {
    const { wrapper } = await mountWithI18n(HeroSection);

    expect(wrapper.findComponent(LaneVisual).exists()).toBe(true);
  });

  it("英文页整屏换英文", async () => {
    const { wrapper } = await mountWithI18n(HeroSection, { locale: "en" });

    expect(wrapper.get(".pill").text()).toContain(COPY.en.hero.pill);
    expect(wrapper.get(".cta .btn-ghost").text()).toBe(COPY.en.ui.hero.allDownloads);
  });
});
