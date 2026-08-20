import { describe, expect, it } from "vitest";
import { detectOS, pickDesktopRelease, primaryMatchKey, type Release } from "./release";

function release(tag: string, assets: string[], extra: Partial<Release> = {}): Release {
  return {
    tag_name: tag,
    assets: assets.map((name) => ({
      name,
      browser_download_url: `https://example.com/${name}`,
    })),
    ...extra,
  };
}

describe("pickDesktopRelease", () => {
  it("挑列表里第一个正式版并解析各平台直链", () => {
    const picked = pickDesktopRelease([
      release("v0.2.0", ["MintPop Lane_0.2.0_aarch64.dmg", "MintPop Lane_0.2.0_x64-setup.exe"]),
      release("v0.1.0", ["MintPop Lane_0.1.0_aarch64.dmg"]),
    ]);
    expect(picked?.version).toBe("0.2.0");
    expect(picked?.urls.MAC_ARM).toContain("aarch64.dmg");
    expect(picked?.urls.WINDOWS).toContain("setup.exe");
  });

  it("跳过预发布与草稿", () => {
    const picked = pickDesktopRelease([
      release("v0.3.0-beta.1", ["a_aarch64.dmg"], { prerelease: true }),
      release("v0.2.9", ["b_aarch64.dmg"], { draft: true }),
      release("v0.2.0", ["c_aarch64.dmg"]),
    ]);
    expect(picked?.version).toBe("0.2.0");
  });

  it("windows 优先 exe，无 exe 时回退 msi", () => {
    const picked = pickDesktopRelease([release("v0.1.0", ["app_x64_zh-CN.msi"])]);
    expect(picked?.urls.WINDOWS).toContain(".msi");
  });

  it("该平台没有对应资产时给 null（由调用方兜底到 Releases 页）", () => {
    const picked = pickDesktopRelease([release("v0.1.0", ["app_x64-setup.exe"])]);
    expect(picked?.urls.MAC_ARM).toBeNull();
    expect(picked?.urls.WINDOWS).not.toBeNull();
  });

  it("列表为空或全是预发布时返回 null", () => {
    expect(pickDesktopRelease([])).toBeNull();
    expect(
      pickDesktopRelease([release("v0.1.0-rc.1", ["a.dmg"], { prerelease: true })]),
    ).toBeNull();
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
