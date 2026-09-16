import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminDeviceRebindRequestResponse } from "../api/types";
import { useRebindStore } from "./rebind";

const listDeviceRebindRequests = vi.fn<() => Promise<AdminDeviceRebindRequestResponse[]>>();

vi.mock("../api", () => ({ adminApi: () => ({ listDeviceRebindRequests }) }));

function request(id: number): AdminDeviceRebindRequestResponse {
  return {
    id,
    requestNo: `DR2026091500000${id}`,
    subscriptionId: 1,
    subscriptionName: "Claude 月付",
    assignmentNo: "7K3M9QX2FT",
    userId: 3,
    userEmail: "u@example.com",
    fromDevice: {
      name: "旧机",
      os: "macos 15.6",
      model: "Mac15,6",
      lastSeenAt: "2026-09-15T09:00:00Z",
    },
    toDevice: {
      name: "新机",
      os: "macos 26.6",
      model: "Mac17,9",
      lastSeenAt: "2026-09-15T11:50:00Z",
    },
    reason: "换了新电脑",
    status: "PENDING",
    createdAt: "2026-09-15T02:00:00Z",
    decidedAt: null,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
});

describe("useRebindStore", () => {
  it("初始为 0，刷新后等于服务端返回的待处理条数", async () => {
    listDeviceRebindRequests.mockResolvedValue([request(1), request(2)]);
    const store = useRebindStore();

    expect(store.pendingCount).toBe(0);
    await store.refresh();

    expect(listDeviceRebindRequests).toHaveBeenCalledWith("PENDING");
    expect(store.pendingCount).toBe(2);
  });

  it("拉取失败时保留上一次的数字，不清零", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([request(1)]);
    const store = useRebindStore();
    await store.refresh();

    listDeviceRebindRequests.mockRejectedValueOnce(new Error("网络抖动"));
    await store.refresh();

    // 清零等于把「有待办」说成「没待办」，比显示一个旧数字更糟
    expect(store.pendingCount).toBe(1);
  });
});
