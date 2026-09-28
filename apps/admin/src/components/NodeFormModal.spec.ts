import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminNodeResponse } from "../api/types";
import NodeFormModal from "./NodeFormModal.vue";

const lookupIpTimezone = vi.fn<(ip: string) => Promise<string | null>>();

vi.mock("../api", () => ({
  adminApi: () => ({ createNode: vi.fn(), updateNode: vi.fn() }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));
vi.mock("../utils/ipTimezone", () => ({
  lookupIpTimezone: (ip: string) => lookupIpTimezone(ip),
}));

function landNode(): AdminNodeResponse {
  return {
    id: 7,
    name: "LAND-东京-01",
    role: "LAND",
    protocol: "SOCKS5",
    serverAddr: "land.example.com",
    port: 1080,
    extraConfig: {},
    egressIp: "203.0.113.7",
    egressTimezone: "Asia/Tokyo",
    status: "ENABLED",
    remark: "",
    secretConfigured: true,
    capacity: 10,
    assignedUserCount: 0,
    airportSubscriptionId: null,
    airportSubscriptionName: null,
    sourceType: null,
    failureDomain: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  lookupIpTimezone.mockResolvedValue(null);
  // jsdom 不实现 scrollIntoView，AdminSelect 展开面板定位高亮项时会调它
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  // AdminModal 用 Teleport 挂到 body，测试间要清掉
  document.body.innerHTML = "";
});

/** Teleport 到 body 的内容 wrapper.find 够不着，须从 document 上取 */
function query<T extends Element = Element>(selector: string): DOMWrapper<T> {
  const el = document.querySelector<T>(selector);
  if (!el) {
    throw new Error(`未找到元素：${selector}`);
  }
  return new DOMWrapper(el);
}

function hint(): string | null {
  return document.querySelector("#node-egress-hint")?.textContent?.trim() ?? null;
}

function mountEditing() {
  return mount(NodeFormModal, {
    attachTo: document.body,
    props: { role: "LAND", editing: landNode() },
  });
}

describe("NodeFormModal 出口 IP 与时区联动", () => {
  it("打开编辑弹窗时原样带出出口 IP 与时区，不出提示、不查 GeoIP", () => {
    mountEditing();

    expect(query<HTMLInputElement>("#node-egress").element.value).toBe("203.0.113.7");
    expect(query<HTMLInputElement>("#node-egress-tz").element.value).toBe("Asia/Tokyo");
    expect(hint()).toBeNull();
    expect(lookupIpTimezone).not.toHaveBeenCalled();
  });

  it("出口 IP 一有输入就清空时区，并提示填好后回车检测；此时不查 GeoIP", async () => {
    mountEditing();

    await query("#node-egress").setValue("203.0.113.");

    expect(query<HTMLInputElement>("#node-egress-tz").element.value).toBe("");
    expect(hint()).toContain("按回车检测时区");
    expect(lookupIpTimezone).not.toHaveBeenCalled();
  });

  it("回车时 IP 格式不对：先提示格式错误（危险色），不查 GeoIP", async () => {
    mountEditing();

    await query("#node-egress").setValue("203.0.113.999");
    await query("#node-egress").trigger("keydown", { key: "Enter" });

    expect(hint()).toContain("不是合法的 IP 地址");
    expect(query("#node-egress-hint").attributes("data-tone")).toBe("DANGER");
    expect(lookupIpTimezone).not.toHaveBeenCalled();
  });

  it("回车时 IP 合法：按 IP 查 GeoIP 填入时区，管理员仍可改", async () => {
    lookupIpTimezone.mockResolvedValue("Asia/Singapore");
    mountEditing();

    await query("#node-egress").setValue("198.51.100.9");
    await query("#node-egress").trigger("keydown", { key: "Enter" });

    await vi.waitFor(() =>
      expect(query<HTMLInputElement>("#node-egress-tz").element.value).toBe("Asia/Singapore"),
    );
    expect(lookupIpTimezone).toHaveBeenCalledWith("198.51.100.9");
    expect(hint()).toContain("已按出口 IP 识别时区");

    await query("#node-egress-tz").setValue("Asia/Shanghai");
    expect(query<HTMLInputElement>("#node-egress-tz").element.value).toBe("Asia/Shanghai");
  });

  it("GeoIP 查不到：提示手动填写，时区保持为空", async () => {
    mountEditing();

    await query("#node-egress").setValue("198.51.100.9");
    await query("#node-egress").trigger("keydown", { key: "Enter" });

    await vi.waitFor(() => expect(hint()).toContain("请手动填写"));
    expect(query<HTMLInputElement>("#node-egress-tz").element.value).toBe("");
  });
});

describe("NodeFormModal 角色下拉不提供第一跳", () => {
  /** 选中项的 <li> 还带一个 ✓ 勾号 span，一并算进 textContent，故用 includes 判断而非整串比对 */
  function hasRoleOption(text: string): boolean {
    return Array.from(document.querySelectorAll("li")).some((li) => li.textContent?.includes(text));
  }

  function frontNode(): AdminNodeResponse {
    return { ...landNode(), id: 9, name: "US-01", role: "FRONT", protocol: "TROJAN" };
  }

  it("新建节点时角色下拉没有「第一跳」——第一跳只能从机场订阅导入", async () => {
    mount(NodeFormModal, { attachTo: document.body, props: { role: "LAND", editing: null } });

    await query('button[aria-label="角色"]').trigger("click");

    expect(hasRoleOption("第一跳（出国）")).toBe(false);
  });

  it("编辑落地节点时角色下拉也没有「第一跳」——不许把节点改成第一跳", async () => {
    mount(NodeFormModal, { attachTo: document.body, props: { role: "LAND", editing: landNode() } });

    await query('button[aria-label="角色"]').trigger("click");

    expect(hasRoleOption("第一跳（出国）")).toBe(false);
  });

  it("编辑历史遗留的手工第一跳节点时，角色下拉仍保留「第一跳」，不强行下架", async () => {
    mount(NodeFormModal, {
      attachTo: document.body,
      props: { role: "FRONT", editing: frontNode() },
    });

    await query('button[aria-label="角色"]').trigger("click");

    expect(hasRoleOption("第一跳（出国）")).toBe(true);
  });
});
