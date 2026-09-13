import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminNodeResponse, NodeProbeResponse, NodeSaveRequest } from "../api/types";
import { showToast } from "../toast";
import NodeProbeModal from "./NodeProbeModal.vue";

const probeNode = vi.fn<(id: number) => Promise<NodeProbeResponse>>();
const updateNode = vi.fn<(id: number, body: NodeSaveRequest) => Promise<void>>();
const lookupIpTimezone = vi.fn<(ip: string) => Promise<string | null>>();

vi.mock("../api", () => ({
  adminApi: () => ({ probeNode, updateNode }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));
vi.mock("../utils/ipTimezone", () => ({
  lookupIpTimezone: (ip: string) => lookupIpTimezone(ip),
}));

function landNode(overrides: Partial<AdminNodeResponse> = {}): AdminNodeResponse {
  return {
    id: 7,
    name: "LAND-东京-01",
    role: "LAND",
    protocol: "SOCKS5",
    serverAddr: "203.0.113.7",
    port: 1080,
    extraConfig: { udp: true },
    egressIp: "203.0.113.7",
    egressTimezone: "Asia/Tokyo",
    status: "ENABLED",
    remark: "备注",
    secretConfigured: true,
    capacity: 10,
    assignedUserCount: 2,
    groupId: null,
    groupName: null,
    sourceType: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

function probeResult(overrides: Partial<NodeProbeResponse> = {}): NodeProbeResponse {
  return {
    reachable: true,
    latencyMs: 420,
    actualEgressIp: "203.0.113.7",
    registeredEgressIp: "203.0.113.7",
    matched: true,
    error: null,
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  lookupIpTimezone.mockResolvedValue(null);
});

afterEach(() => {
  // AdminModal 用 Teleport 挂到 body，测试间要清掉，不然下一次挂载撞上上一次残留的 DOM
  document.body.innerHTML = "";
});

/** AdminModal 用 Teleport 挂到 body，游离节点里的内容 wrapper.find 够不着，须从 document 上取 */
function query(selector: string): DOMWrapper<Element> {
  const el = document.querySelector(selector);
  if (!el) {
    throw new Error(`未找到元素：${selector}`);
  }
  return new DOMWrapper(el);
}

function fillButton(): Element | null {
  return document.querySelector("button.admin-btn");
}

function mountModal(node: AdminNodeResponse = landNode()) {
  return mount(NodeProbeModal, { attachTo: document.body, props: { node } });
}

describe("NodeProbeModal", () => {
  it("挂载即发起检测；一致时展示两侧 IP 与耗时，不提供填入", async () => {
    probeNode.mockResolvedValue(probeResult());
    mountModal();

    expect(probeNode).toHaveBeenCalledWith(7);
    await vi.waitFor(() => expect(query("#probe-actual-ip").text()).toBe("203.0.113.7"));
    expect(query("#probe-registered-ip").text()).toBe("203.0.113.7");
    expect(query("#probe-verdict").text()).toContain("一致");
    expect(document.body.textContent).toContain("420 ms");
    expect(fillButton()).toBeNull();
  });

  it("不一致时提供「填入出口 IP」：用实际 IP 覆盖登记值整体保存，密码传空对象沿用原值", async () => {
    probeNode.mockResolvedValue(probeResult({ actualEgressIp: "198.51.100.9", matched: false }));
    updateNode.mockResolvedValue();
    const wrapper = mountModal();

    await vi.waitFor(() => expect(query("#probe-verdict").text()).toContain("不一致"));
    await query("button.admin-btn").trigger("click");

    await vi.waitFor(() => expect(updateNode).toHaveBeenCalledTimes(1));
    const [id, body] = updateNode.mock.calls[0];
    expect(id).toBe(7);
    expect(body).toMatchObject({
      name: "LAND-东京-01",
      role: "LAND",
      protocol: "SOCKS5",
      serverAddr: "203.0.113.7",
      port: 1080,
      extraConfig: { udp: true },
      secret: {},
      egressIp: "198.51.100.9",
      egressTimezone: "Asia/Tokyo",
      capacity: 10,
      status: "ENABLED",
      remark: "备注",
    });
    // 已有时区不动，不去查 GeoIP
    expect(lookupIpTimezone).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(wrapper.emitted("saved")).toBeTruthy());
    expect(wrapper.emitted("close")).toBeTruthy();
    expect(showToast).toHaveBeenCalledWith("success", expect.stringContaining("198.51.100.9"));
  });

  it("未登记出口 IP 且时区为空：填入时顺带按 GeoIP 预填时区", async () => {
    probeNode.mockResolvedValue(
      probeResult({ actualEgressIp: "198.51.100.9", registeredEgressIp: null, matched: null }),
    );
    updateNode.mockResolvedValue();
    lookupIpTimezone.mockResolvedValue("Asia/Singapore");
    mountModal(landNode({ egressIp: null, egressTimezone: null }));

    await vi.waitFor(() => expect(query("#probe-verdict").text()).toContain("未登记"));
    await query("button.admin-btn").trigger("click");

    await vi.waitFor(() => expect(updateNode).toHaveBeenCalledTimes(1));
    expect(lookupIpTimezone).toHaveBeenCalledWith("198.51.100.9");
    expect(updateNode.mock.calls[0][1]).toMatchObject({
      egressIp: "198.51.100.9",
      egressTimezone: "Asia/Singapore",
    });
  });

  it("不通时展示原因、不提供填入，「重新检测」再发一次探测", async () => {
    probeNode
      .mockResolvedValueOnce(
        probeResult({ reachable: false, actualEgressIp: null, matched: null, error: "connect timed out" }),
      )
      .mockResolvedValueOnce(probeResult());
    mountModal();

    await vi.waitFor(() => expect(query("#probe-verdict").text()).toContain("不通"));
    expect(document.body.textContent).toContain("connect timed out");
    expect(fillButton()).toBeNull();

    await query("#probe-retry").trigger("click");
    await vi.waitFor(() => expect(probeNode).toHaveBeenCalledTimes(2));
    await vi.waitFor(() => expect(query("#probe-verdict").text()).toContain("一致"));
  });

  it("检测请求本身报业务错时 toast 提示，弹窗留在可重试状态", async () => {
    probeNode.mockRejectedValue(new BizError(410040, "只有落地节点支持出口检测"));
    mountModal();

    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("error", "只有落地节点支持出口检测"));
    expect(fillButton()).toBeNull();
    expect(document.querySelector("#probe-retry")).not.toBeNull();
  });
});
