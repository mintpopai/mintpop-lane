import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminNodeResponse, LinkHealthResponse } from "../api/types";
import DataCard from "../components/DataCard.vue";
import LinkHealthView from "./LinkHealthView.vue";

const getLinkHealth = vi.fn<(days?: number) => Promise<LinkHealthResponse>>();
// 页面不该用到它——「不渲染任何按节点的行」那条测试专门守这个诱惑（见下）
const listNodes = vi.fn<() => Promise<AdminNodeResponse[]>>(async () => []);

vi.mock("../api", () => ({ adminApi: () => ({ getLinkHealth, listNodes }) }));

function domain(overrides: Partial<LinkHealthResponse["domains"][number]> = {}) {
  return {
    failureDomain: "front-a.example.com",
    samples: 100,
    aliveCount: 90,
    failovers: 2,
    isps: [{ isp: "中国电信", samples: 100, aliveCount: 90, successRate: 0.9 }],
    ...overrides,
  };
}

function health(overrides: Partial<LinkHealthResponse> = {}): LinkHealthResponse {
  return {
    domains: [domain()],
    entryIpTimeline: [],
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  listNodes.mockResolvedValue([]);
  getLinkHealth.mockResolvedValue(health());
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(LinkHealthView, { attachTo: document.body });
  await flushPromises();
  return wrapper;
}

describe("LinkHealthView 加载", () => {
  it("进页就按默认天数拉一次数据", async () => {
    await render();

    expect(getLinkHealth).toHaveBeenCalledOnce();
    expect(getLinkHealth).toHaveBeenCalledWith(7);
  });

  it("接口失败时显示错误态而不是空矩阵", async () => {
    getLinkHealth.mockRejectedValueOnce(new BizError(410001, "没权限看链路健康"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("没权限看链路健康");
    // 出错时矩阵不该残留任何一行
    expect(wrapper.findAll(".domain-group")).toHaveLength(0);
  });
});

describe("LinkHealthView 矩阵展示（spec §8.3）", () => {
  it("没有样本的格子显示「无数据」，不显示 0%", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            isps: [{ isp: "中国联通", samples: 0, aliveCount: 0, successRate: null }],
          }),
        ],
      }),
    );
    const wrapper = await render();

    // 只看这一个格子（成功率列），不看整页文本——域级聚合的「90.0%」这类合法文案
    // 尾巴恰好也是「0%」，混在一起断言会把误报当成真信号
    const rateCell = wrapper.get("tbody tr td:last-child");
    expect(rateCell.text()).toBe("无数据");
    expect(rateCell.text()).not.toContain("%");
  });

  it("故障域为空串时显示「未解析」并带警示", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        domains: [domain({ failureDomain: "" })],
      }),
    );
    const wrapper = await render();

    expect(wrapper.text()).toContain("未解析");
    // 视觉提示：这一行要挂上专门的警示样式，不能只是纯文本混在一起
    const group = wrapper.get(".domain-group");
    expect(group.classes()).toContain("domain-unresolved");
    expect(group.find(".domain-unresolved-warning").exists()).toBe(true);
  });

  it("运营商反查失败（空串）显示「未知运营商」", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            isps: [{ isp: "", samples: 5, aliveCount: 4, successRate: 0.8 }],
          }),
        ],
      }),
    );
    const wrapper = await render();

    expect(wrapper.text()).toContain("未知运营商");
  });

  it("有数据的格子显示百分比成功率", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            isps: [{ isp: "中国移动", samples: 40, aliveCount: 38, successRate: 0.95 }],
          }),
        ],
      }),
    );
    const wrapper = await render();

    expect(wrapper.text()).toContain("95.0%");
  });

  it("不渲染任何按节点的行", async () => {
    // 就算别处（如节点池）存在这些名字，链路健康页也不该按节点展开——
    // 同一故障域下的节点共用一台中转入口机，不是独立样本
    listNodes.mockResolvedValue([
      {
        id: 1,
        name: "美西-前置-01",
        role: "FRONT",
        protocol: "TROJAN",
        serverAddr: "1.2.3.4",
        port: 443,
        extraConfig: {},
        egressIp: null,
        egressTimezone: null,
        status: "ENABLED",
        remark: null,
        secretConfigured: true,
        capacity: null,
        assignedUserCount: null,
        groupId: null,
        groupName: null,
        sourceType: null,
        failureDomain: "front-a.example.com",
        createdAt: "2026-09-01T00:00:00Z",
        updatedAt: "2026-09-01T00:00:00Z",
      },
    ]);
    const wrapper = await render();

    expect(listNodes).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain("美西-前置-01");
    // 表格里也不该多出一列「节点」
    expect(wrapper.text()).not.toContain("节点名");
  });

  it("多个故障域各自成组，组内按运营商展开多行", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            failureDomain: "front-a.example.com",
            isps: [{ isp: "中国电信", samples: 10, aliveCount: 9, successRate: 0.9 }],
          }),
          domain({
            failureDomain: "front-b.example.com",
            isps: [
              { isp: "中国电信", samples: 10, aliveCount: 8, successRate: 0.8 },
              { isp: "中国联通", samples: 10, aliveCount: 10, successRate: 1 },
            ],
          }),
        ],
      }),
    );
    const wrapper = await render();

    const groups = wrapper.findAll(".domain-group");
    expect(groups).toHaveLength(2);
    expect(groups[1].findAll("tbody tr")).toHaveLength(2);
  });
});

describe("LinkHealthView 入口 IP 变更时间线", () => {
  it("有变更记录时按倒序列出", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        entryIpTimeline: [
          {
            failureDomain: "front-a.example.com",
            vantage: "CHINA_TELECOM",
            previousIps: "1.1.1.1",
            currentIps: "2.2.2.2",
            changedAt: "2026-09-18T03:00:00Z",
          },
        ],
      }),
    );
    const wrapper = await render();

    expect(wrapper.text()).toContain("1.1.1.1");
    expect(wrapper.text()).toContain("2.2.2.2");
  });

  it("没有变更记录时不留空表格", async () => {
    const wrapper = await render();

    expect(wrapper.text()).toContain("暂无");
  });
});

describe("LinkHealthView 天数切换", () => {
  it("切换天数后按新天数重新拉取", async () => {
    const wrapper = await render();

    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", 30);
    await flushPromises();

    expect(getLinkHealth).toHaveBeenLastCalledWith(30);
  });
});
