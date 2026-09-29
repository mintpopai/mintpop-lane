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
    asns: [
      { asn: "AS4134", orgName: "China Telecom", samples: 100, aliveCount: 90, successRate: 0.9 },
    ],
    ...overrides,
  };
}

function health(overrides: Partial<LinkHealthResponse> = {}): LinkHealthResponse {
  return {
    domains: [domain()],
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
            asns: [
              {
                asn: "AS4837",
                orgName: "China Unicom",
                samples: 0,
                aliveCount: 0,
                successRate: null,
              },
            ],
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

  it("成功率跌破告警阈值的格子标红，阈值之上的不标", async () => {
    // 这个页面的用途是「一眼看出哪里不对」。加这条之前，40.0% 与 96.4% 的颜色、
    // 字重、背景在真实浏览器里完全相同（实测量过 computed style），得逐个读数字
    // 才看得出问题。标红的门槛与服务端 link-report.alert-threshold 同一条线：
    // 页面上红的，正是服务端会推飞书的那些
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            asns: [
              {
                asn: "AS9808",
                orgName: "China Mobile",
                samples: 100,
                aliveCount: 40,
                successRate: 0.4,
              },
              {
                asn: "AS4134",
                orgName: "China Telecom",
                samples: 100,
                aliveCount: 96,
                successRate: 0.96,
              },
            ],
          }),
        ],
      }),
    );
    const wrapper = await render();

    const cells = wrapper.findAll("tbody tr td:last-child");
    expect(cells[0].text()).toBe("40.0%");
    expect(cells[0].classes()).toContain("cell-degraded");
    expect(cells[1].text()).toBe("96.0%");
    expect(cells[1].classes()).not.toContain("cell-degraded");
  });

  it("没有样本的格子不标红——「没有数据」不是「跌破阈值」", async () => {
    // 守的是把 null 也当成低成功率标红：那会让人以为出了故障，
    // 实际只是这段时间没人从那个运营商上来。与上一条是相反方向的两个错
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            asns: [
              {
                asn: "AS4837",
                orgName: "China Unicom",
                samples: 0,
                aliveCount: 0,
                successRate: null,
              },
            ],
          }),
        ],
      }),
    );
    const wrapper = await render();

    const rateCell = wrapper.get("tbody tr td:last-child");
    expect(rateCell.text()).toBe("无数据");
    expect(rateCell.classes()).not.toContain("cell-degraded");
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
            asns: [{ asn: "", orgName: null, samples: 5, aliveCount: 4, successRate: 0.8 }],
          }),
        ],
      }),
    );
    const wrapper = await render();

    expect(wrapper.text()).toContain("未知运营商");
  });

  it("运营商列：有 orgName 显示名字并附 ASN 串，无 orgName 显示 ASN 串，asn 空串显示「未知运营商」", async () => {
    // 三种「空」的语义完全不同，页面上必须分得开：
    // - asn 空串 = 这组样本的 ASN 反查全失败，连是谁都不知道；
    // - orgName 为 null = ASN 知道，但 asn_org 里还没记过展示名（旁路写入允许缺）。
    //   这时退回显示 AS 号——人认不出是哪家，但绝不能因此把整列藏起来：少一列
    //   等于凭空丢掉一批真实流量，比显示一串 AS 号糟得多；
    // - 有 orgName = 名字在前（人一眼认得出），ASN 串弱化附在后面备查——
    //   名字是上游自由文本、会漂，真要查证还得看 AS 号
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            asns: [
              { asn: "", orgName: null, samples: 5, aliveCount: 4, successRate: 0.8 },
              {
                asn: "AS4134",
                orgName: "China Telecom",
                samples: 10,
                aliveCount: 9,
                successRate: 0.9,
              },
              { asn: "AS9808", orgName: null, samples: 10, aliveCount: 9, successRate: 0.9 },
            ],
          }),
        ],
      }),
    );
    const wrapper = await render();

    const labels = wrapper.findAll("tbody tr td:first-child");
    expect(labels).toHaveLength(3);
    expect(labels[0].text()).toBe("未知运营商");
    expect(labels[1].text()).toContain("China Telecom");
    expect(labels[1].get(".asn-code").text()).toBe("AS4134");
    // 没有展示名时直接显示 AS 号本身，且不留一个空的附注位
    expect(labels[2].text()).toBe("AS9808");
    expect(labels[2].find(".asn-code").exists()).toBe(false);
  });

  it("有数据的格子显示百分比成功率", async () => {
    getLinkHealth.mockResolvedValue(
      health({
        domains: [
          domain({
            asns: [
              {
                asn: "AS9808",
                orgName: "China Mobile",
                samples: 40,
                aliveCount: 38,
                successRate: 0.95,
              },
            ],
          }),
        ],
      }),
    );
    const wrapper = await render();

    expect(wrapper.text()).toContain("95.0%");
  });

  it("不渲染任何按节点的行", async () => {
    // 就算别处（如订阅详情页）存在这些名字，链路健康页也不该按节点展开——
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
        airportSubscriptionId: null,
        airportSubscriptionName: null,
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
            asns: [
              {
                asn: "AS4134",
                orgName: "China Telecom",
                samples: 10,
                aliveCount: 9,
                successRate: 0.9,
              },
            ],
          }),
          domain({
            failureDomain: "front-b.example.com",
            asns: [
              {
                asn: "AS4134",
                orgName: "China Telecom",
                samples: 10,
                aliveCount: 8,
                successRate: 0.8,
              },
              {
                asn: "AS4837",
                orgName: "China Unicom",
                samples: 10,
                aliveCount: 10,
                successRate: 1,
              },
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

describe("LinkHealthView 天数切换", () => {
  it("切换天数后按新天数重新拉取", async () => {
    const wrapper = await render();

    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", 30);
    await flushPromises();

    expect(getLinkHealth).toHaveBeenLastCalledWith(30);
  });
});
