import { describe, expect, it } from "vitest";
import { lookupIpTimezone } from "./ipTimezone";

function fakeFetch(body: unknown, ok = true): typeof fetch {
  return async () =>
    ({
      ok,
      json: async () => body,
    }) as Response;
}

describe("lookupIpTimezone", () => {
  it("查询成功时返回 IANA 时区名", async () => {
    const fetchFn = fakeFetch({ success: true, timezone: { id: "Asia/Tokyo" } });

    expect(await lookupIpTimezone("203.0.113.10", fetchFn)).toBe("Asia/Tokyo");
  });

  it("服务返回 success=false（如保留地址查不到）时返回 null", async () => {
    const fetchFn = fakeFetch({ success: false, message: "Reserved range" });

    expect(await lookupIpTimezone("192.168.1.1", fetchFn)).toBeNull();
  });

  it("HTTP 非 2xx 时返回 null，不抛错——预填失败降级为人工填写", async () => {
    const fetchFn = fakeFetch({}, false);

    expect(await lookupIpTimezone("203.0.113.10", fetchFn)).toBeNull();
  });

  it("网络异常时返回 null，不抛错", async () => {
    const fetchFn: typeof fetch = async () => {
      throw new TypeError("Failed to fetch");
    };

    expect(await lookupIpTimezone("203.0.113.10", fetchFn)).toBeNull();
  });

  it("响应形状不对（没有 timezone.id）时返回 null", async () => {
    const fetchFn = fakeFetch({ success: true, timezone: "不是对象" });

    expect(await lookupIpTimezone("203.0.113.10", fetchFn)).toBeNull();
  });
});
