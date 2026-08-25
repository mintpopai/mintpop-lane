import { describe, expect, it } from "vitest";
import {
  detectOS,
  formatSize,
  parseDownloads,
  primaryMatchKey,
  type DownloadsManifest,
} from "./release";

function manifest(overrides: Partial<DownloadsManifest> = {}): DownloadsManifest {
  return {
    version: "0.4.0",
    pubDate: "2026-08-24T12:00:00Z",
    platforms: {
      "darwin-aarch64": {
        url: "https://dl.mintpop.ai/lane/v0.4.0/MintPop-Lane_0.4.0_aarch64.dmg",
        size: 33_554_432,
      },
      "windows-x86_64": {
        url: "https://dl.mintpop.ai/lane/v0.4.0/MintPop-Lane_0.4.0_x64-setup.exe",
        size: 35_651_584,
      },
    },
    ...overrides,
  };
}

describe("parseDownloads", () => {
  it("按平台键取出各平台直链与体积，不靠文件名后缀猜", () => {
    const parsed = parseDownloads(manifest());

    expect(parsed?.version).toBe("0.4.0");
    expect(parsed?.platforms.MAC_ARM?.url).toContain("aarch64.dmg");
    expect(parsed?.platforms.MAC_ARM?.size).toBe(33_554_432);
    expect(parsed?.platforms.WINDOWS?.url).toContain("setup.exe");
  });

  it("清单里缺某个平台时该平台给 null，另一个不受影响", () => {
    const parsed = parseDownloads(
      manifest({
        platforms: { "windows-x86_64": { url: "https://example.com/a.exe", size: 1 } },
      }),
    );

    expect(parsed?.platforms.MAC_ARM).toBeNull();
    expect(parsed?.platforms.WINDOWS).not.toBeNull();
  });

  it("体积字段缺失或非数字时降级为 0，不影响直链可用", () => {
    const parsed = parseDownloads(
      manifest({
        platforms: { "darwin-aarch64": { url: "https://example.com/a.dmg" } as never },
      }),
    );

    expect(parsed?.platforms.MAC_ARM?.url).toBe("https://example.com/a.dmg");
    expect(parsed?.platforms.MAC_ARM?.size).toBe(0);
  });

  it("清单形状不对时整体给 null，绝不让 undefined 漏进 href", () => {
    expect(parseDownloads(null)).toBeNull();
    expect(parseDownloads("nope")).toBeNull();
    expect(parseDownloads({})).toBeNull();
    expect(parseDownloads({ version: "", platforms: {} })).toBeNull();
    expect(parseDownloads({ version: "0.4.0" })).toBeNull();
  });

  it("平台条目缺 url 时按拿不到处理", () => {
    const parsed = parseDownloads(
      manifest({ platforms: { "darwin-aarch64": { size: 123 } as never } }),
    );

    expect(parsed?.platforms.MAC_ARM).toBeNull();
  });
});

describe("formatSize", () => {
  it("字节数换算成 MB", () => {
    expect(formatSize(33_554_432)).toBe("约 32 MB");
  });

  it("拿不到有效体积时给 null，调用方不渲染这一段", () => {
    expect(formatSize(0)).toBeNull();
    expect(formatSize(-1)).toBeNull();
    expect(formatSize(Number.NaN)).toBeNull();
  });
});

describe("detectOS", () => {
  const macUA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)";
  const winUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
  const iphoneUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";

  it("识别 mac 与 windows", () => {
    expect(detectOS(macUA)).toBe("MAC");
    expect(detectOS(winUA)).toBe("WINDOWS");
  });

  it("移动端一律 OTHER：iPhone 的 UA 含 like Mac OS X，必须先排除", () => {
    expect(detectOS(iphoneUA)).toBe("OTHER");
  });

  it("iPadOS 桌面模式 UA 与真 Mac 相同，靠触点数区分", () => {
    expect(detectOS(macUA, 5)).toBe("OTHER");
    expect(detectOS(macUA, 0)).toBe("MAC");
  });
});

describe("primaryMatchKey", () => {
  it("mac 默认 Apple 芯片，windows 给 WINDOWS，其它系统无主按钮", () => {
    expect(primaryMatchKey("MAC")).toBe("MAC_ARM");
    expect(primaryMatchKey("WINDOWS")).toBe("WINDOWS");
    expect(primaryMatchKey("OTHER")).toBeNull();
  });
});
