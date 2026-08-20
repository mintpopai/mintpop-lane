import { beforeEach, describe, expect, it } from "vitest";
import { 标记登录跳转, 清除标记, 疑似环路 } from "./loginLoop";

describe("登录环路熔断", () => {
  beforeEach(() => sessionStorage.clear());

  it("没打过标记就不算环路——首次进站不该被当成死循环", () => {
    expect(疑似环路(1_000_000)).toBe(false);
  });

  it("刚跳过登录又立刻回到未登录，判为环路", () => {
    标记登录跳转(1_000_000);

    expect(疑似环路(1_000_000 + 14_999)).toBe(true);
  });

  it("超过判定窗口后不再算环路——隔天再来是正常的重新登录", () => {
    标记登录跳转(1_000_000);

    expect(疑似环路(1_000_000 + 15_000)).toBe(false);
  });

  it("清除标记后回到「没打过标记」的状态", () => {
    标记登录跳转(1_000_000);
    清除标记();

    expect(疑似环路(1_000_000 + 1)).toBe(false);
  });

  it("标记被改成非数字时按没标记处理，不误判成环路", () => {
    sessionStorage.setItem("lane.loginRedirectAt", "坏掉的值");

    expect(疑似环路(1_000_000)).toBe(false);
  });
});
