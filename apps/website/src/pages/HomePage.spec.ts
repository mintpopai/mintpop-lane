import { describe, expect, it, vi } from "vitest";
import DownloadSection from "../sections/DownloadSection.vue";
import FaqSection from "../sections/FaqSection.vue";
import GuidesSection from "../sections/GuidesSection.vue";
import HeroSection from "../sections/HeroSection.vue";
import LaneSection from "../sections/LaneSection.vue";
import PricingSection from "../sections/PricingSection.vue";
import StepsSection from "../sections/StepsSection.vue";
import TerminalSection from "../sections/TerminalSection.vue";
import VerifySection from "../sections/VerifySection.vue";
import { COPY } from "../content/copy";
import { SITE } from "../routes";
import { mountWithI18n, readHead } from "../testing";
import HomePage from "./HomePage.vue";

// 首页会连带挂起 Hero 与下载区，它们要拉分发清单；清单本身在 useRelease.spec.ts 里测，
// 这里换掉它，免得每个用例都去摆弄 fetch。
vi.mock("../composables/useRelease", async () => {
  const { computed, ref } = await import("vue");
  return {
    useRelease: () => ({
      os: ref("OTHER" as const),
      version: computed(() => null),
      primaryKey: computed(() => null),
      platformFor: () => null,
    }),
  };
});

/** 从 head 里挑出指定类型的那段结构化数据 */
function ldOf(all: Record<string, unknown>[], type: string) {
  return all.find((x) => x["@type"] === type);
}

describe("HomePage 叙事顺序", () => {
  it("九个区块齐全，顺序即叙事顺序：多少钱 → 省什么事 → 出问题怎么办 → 怎么用 → 下载", async () => {
    const { wrapper } = await mountWithI18n(HomePage);

    const order = [
      HeroSection,
      PricingSection,
      LaneSection,
      VerifySection,
      TerminalSection,
      StepsSection,
      DownloadSection,
      GuidesSection,
      FaqSection,
    ];
    for (const section of order) expect(wrapper.findComponent(section).exists()).toBe(true);

    // 用 DOM 里 id 出现的先后核对顺序（只有带 id 的区块能这么查）
    const html = wrapper.html();
    const at = (id: string) => html.indexOf(`id="${id}"`);
    expect(at("pricing")).toBeLessThan(at("lane"));
    expect(at("lane")).toBeLessThan(at("verify"));
    expect(at("verify")).toBeLessThan(at("terminal"));
    expect(at("terminal")).toBeLessThan(at("download"));
    expect(at("download")).toBeLessThan(at("guides"));
    expect(at("guides")).toBeLessThan(at("faq"));
  });
});

describe("HomePage 的 head", () => {
  it("标题与描述来自本语言文案，并同步给社交卡片", async () => {
    const { head } = await mountWithI18n(HomePage);
    const { title, meta } = await readHead(head);

    const { title: t, description } = COPY.zh.meta;
    expect(title).toBe(t);
    expect(meta.description).toBe(description);
    expect(meta["og:title"]).toBe(t);
    expect(meta["og:description"]).toBe(description);
    expect(meta["twitter:title"]).toBe(t);
    expect(meta["twitter:description"]).toBe(description);
    // 社交卡片图的 alt 也得有，否则读屏里那张图是哑的
    expect(meta["og:image:alt"]).toBe(t);
  });

  it("英文页换英文标题与描述", async () => {
    const { head } = await mountWithI18n(HomePage, { locale: "en" });
    const { title, meta } = await readHead(head);

    expect(title).toBe(COPY.en.meta.title);
    expect(meta.description).toBe(COPY.en.meta.description);
  });

  it("输出 SoftwareApplication 结构化数据，url 与下载入口都指向本语言首页", async () => {
    const { head } = await mountWithI18n(HomePage);
    const ld = ldOf((await readHead(head)).jsonLd, "SoftwareApplication")!;

    expect(ld.name).toBe("MintPop Lane");
    expect(ld.applicationCategory).toBe("DeveloperApplication");
    expect(ld.url).toBe(`${SITE}/`);
    expect(ld.downloadUrl).toBe(`${SITE}/#download`);
    expect(ld.inLanguage).toBe("zh-CN");
    // 只出 Apple 芯片与 Windows x64——与桌面端目标平台一致，不列 Intel mac
    expect(String(ld.operatingSystem)).toContain("Apple silicon");
    expect(String(ld.operatingSystem)).not.toContain("Intel");
  });

  it("英文页的结构化数据指向 /en/，语言也跟着换", async () => {
    const { head } = await mountWithI18n(HomePage, { locale: "en" });
    const ld = ldOf((await readHead(head)).jsonLd, "SoftwareApplication")!;

    expect(ld.url).toBe(`${SITE}/en/`);
    expect(ld.downloadUrl).toBe(`${SITE}/en/#download`);
    expect(ld.inLanguage).toBe("en");
  });

  it("首页同时带上 FAQ 那段富摘要（由 FaqSection 输出），两段结构化数据并存", async () => {
    const { head } = await mountWithI18n(HomePage);
    const all = (await readHead(head)).jsonLd;

    expect(ldOf(all, "SoftwareApplication")).toBeDefined();
    expect(ldOf(all, "FAQPage")).toBeDefined();
  });

  it("路径相关的 head 不在这里出，交给 App.vue 统一算，免得两处打架", async () => {
    const { head } = await mountWithI18n(HomePage);
    const { links, meta } = await readHead(head);

    expect(links.filter((l) => l.rel === "canonical")).toHaveLength(0);
    expect(meta["og:url"]).toBeUndefined();
  });
});
