import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { showToast, toast } from "./toast";

beforeEach(() => {
  vi.useFakeTimers();
  toast.value = null;
});

afterEach(() => {
  vi.useRealTimers();
});

describe("showToast", () => {
  it("把类型与文案挂上去，供 App.vue 渲染", () => {
    showToast("success", "已保存");

    expect(toast.value).toEqual({ type: "success", text: "已保存" });
  });

  it("3 秒后自动收走，不用调用方手动关", () => {
    showToast("error", "保存失败");

    vi.advanceTimersByTime(2999);
    expect(toast.value).not.toBeNull();

    vi.advanceTimersByTime(1);
    expect(toast.value).toBeNull();
  });

  it("连着提示两次时重新计时：后一条也能完整显示 3 秒", () => {
    showToast("success", "第一条");
    vi.advanceTimersByTime(2500);

    showToast("success", "第二条");
    expect(toast.value?.text).toBe("第二条");

    // 若旧计时器没被清掉，这里会在第二条只显示 500ms 时把它收走
    vi.advanceTimersByTime(600);
    expect(toast.value?.text).toBe("第二条");

    vi.advanceTimersByTime(2400);
    expect(toast.value).toBeNull();
  });
});
