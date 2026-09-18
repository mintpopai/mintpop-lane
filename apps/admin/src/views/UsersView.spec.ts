import { DOMWrapper, flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminUserResponse, PageResult, UserSaveRequest } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import { showToast } from "../toast";
import TruncatedText from "../components/TruncatedText.vue";
import UsersView from "./UsersView.vue";

type PageQuery = {
  keyword: string;
  hasActiveSubscription: boolean | null;
  pageNo: number;
  pageSize: number;
};

const pageUsers = vi.fn<(query: PageQuery) => Promise<PageResult<AdminUserResponse>>>();
const updateUser = vi.fn<(id: number, body: UserSaveRequest) => Promise<void>>(
  async () => undefined,
);
const deleteUser = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({ adminApi: () => ({ pageUsers, updateUser, deleteUser }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function user(overrides: Partial<AdminUserResponse> = {}): AdminUserResponse {
  return {
    id: 1,
    subject: "sub-1",
    email: "a@acme.com",
    role: "MEMBER",
    status: "ACTIVE",
    frontNodeId: null,
    frontNodeName: null,
    frontNodes: [],
    failureDomainCount: 0,
    landNodeId: null,
    landNodeName: null,
    egressIp: null,
    activeSubscriptions: [],
    remark: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T03:04:05Z",
    ...overrides,
  };
}

function page(records: AdminUserResponse[], total = records.length) {
  return { records, total, pageNo: 1, pageSize: 20 } as PageResult<AdminUserResponse>;
}

beforeEach(() => {
  vi.clearAllMocks();
  pageUsers.mockResolvedValue(page([user()]));
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(UsersView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
  });
  await flushPromises();
  return wrapper;
}

/** 某一行操作列里，文案为 label 的那个按钮 */
function action(
  wrapper: Awaited<ReturnType<typeof render>>,
  rowIndex: number,
  label: string,
): DOMWrapper<HTMLButtonElement> {
  const found = wrapper
    .findAll("tbody tr")
    [rowIndex].findAll<HTMLButtonElement>(".actions button")
    .find((b) => b.text() === label);
  if (!found) {
    throw new Error(`第 ${rowIndex} 行没有「${label}」按钮`);
  }
  return found;
}

describe("UsersView 管理员账号受保护", () => {
  beforeEach(() => {
    pageUsers.mockResolvedValue(page([user({ id: 9, email: "boss@acme.com", role: "ADMIN" })]));
  });

  it("停用、吊销、删除三个口子对管理员一律禁用", async () => {
    const wrapper = await render();

    for (const label of ["停用", "吊销", "删除"]) {
      expect(action(wrapper, 0, label).element.disabled).toBe(true);
    }
  });

  it("按钮照常渲染、只是禁用：隐藏会让操作列错位，也让人猜不到为什么没有", async () => {
    const wrapper = await render();

    const labels = wrapper
      .findAll("tbody tr")[0]
      .findAll(".actions button")
      .map((b) => b.text());
    expect(labels).toEqual(["停用", "吊销", "删除"]);
  });

  it("把原因说在原地，鼠标悬上去就知道为什么点不动", async () => {
    const wrapper = await render();

    expect(action(wrapper, 0, "停用").attributes("title")).toBe("管理员账号受保护，不允许停用");
    expect(action(wrapper, 0, "吊销").attributes("title")).toBe("管理员账号受保护，不允许吊销");
    expect(action(wrapper, 0, "删除").attributes("title")).toBe("管理员账号受保护，不允许删除");
  });

  it("普通成员不带这个 title，也点得动", async () => {
    pageUsers.mockResolvedValue(page([user({ role: "MEMBER" })]));
    const wrapper = await render();

    expect(action(wrapper, 0, "停用").element.disabled).toBe(false);
    expect(action(wrapper, 0, "停用").attributes("title")).toBeUndefined();
  });
});

describe("UsersView 处置态转换", () => {
  it("正常的用户可停用：可逆操作，点了直接生效，不走二次确认", async () => {
    const wrapper = await render();

    await action(wrapper, 0, "停用").trigger("click");
    await flushPromises();

    expect(updateUser).toHaveBeenCalledWith(1, {
      status: "SUSPENDED",
      frontNodeId: null,
      reallocateFront: false,
      landNodeId: null,
      remark: "",
    });
    expect(showToast).toHaveBeenCalledWith("success", "已停用");
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(false);
  });

  it("停用中的用户给的是「恢复」，不再给「停用」", async () => {
    pageUsers.mockResolvedValue(page([user({ status: "SUSPENDED" })]));
    const wrapper = await render();

    await action(wrapper, 0, "恢复").trigger("click");
    await flushPromises();

    expect(updateUser).toHaveBeenCalledWith(1, expect.objectContaining({ status: "ACTIVE" }));
    expect(showToast).toHaveBeenCalledWith("success", "已恢复");
  });

  it("改状态时把节点分配原样带回，不会顺手把人的链路清了", async () => {
    pageUsers.mockResolvedValue(page([user({ frontNodeId: 4, landNodeId: 7 })]));
    const wrapper = await render();

    await action(wrapper, 0, "停用").trigger("click");
    await flushPromises();

    expect(updateUser).toHaveBeenCalledWith(1, {
      status: "SUSPENDED",
      frontNodeId: 4,
      reallocateFront: false,
      landNodeId: 7,
      remark: "",
    });
  });

  it("改状态绝不要求重新分配前置组——整体保存接口，顺手重算等于把人的第一跳换掉", async () => {
    pageUsers.mockResolvedValue(page([user({ frontNodeId: 4, landNodeId: 7 })]));
    const wrapper = await render();

    await action(wrapper, 0, "停用").trigger("click");
    await flushPromises();

    expect(updateUser).toHaveBeenCalledWith(1, expect.objectContaining({ reallocateFront: false }));
  });

  it("改状态时备注也原样带回——整体保存接口，不带就等于顺手清空", async () => {
    pageUsers.mockResolvedValue(page([user({ remark: "老客户，续费谈过" })]));
    const wrapper = await render();

    await action(wrapper, 0, "停用").trigger("click");
    await flushPromises();

    expect(updateUser).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ remark: "老客户，续费谈过" }),
    );
  });

  it("已吊销是终态，只剩删除一个口子", async () => {
    pageUsers.mockResolvedValue(page([user({ status: "REVOKED" })]));
    const wrapper = await render();

    const labels = wrapper
      .findAll("tbody tr")[0]
      .findAll(".actions button")
      .map((b) => b.text());
    expect(labels).toEqual(["删除"]);
  });

  it("状态改不动时提示原因，列表不刷新", async () => {
    updateUser.mockRejectedValueOnce(new BizError(410030, "该用户正在使用中"));
    const wrapper = await render();

    await action(wrapper, 0, "停用").trigger("click");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "该用户正在使用中");
    expect(pageUsers).toHaveBeenCalledOnce();
  });
});

describe("UsersView 吊销", () => {
  it("吊销是终态，走二次确认，并把「不可恢复」说在确认框里", async () => {
    const wrapper = await render();

    await action(wrapper, 0, "吊销").trigger("click");

    const confirm = wrapper.findComponent(ConfirmDialog);
    expect(confirm.props("title")).toBe("吊销确认");
    expect(confirm.props("confirmText")).toBe("吊销");
    const message = String(confirm.props("message"));
    expect(message).toContain("a@acme.com");
    expect(message).toContain("不可再恢复");
    // 还没确认，不该发请求
    expect(updateUser).not.toHaveBeenCalled();
  });

  it("确认后才吊销，成功后刷新列表", async () => {
    const wrapper = await render();
    await action(wrapper, 0, "吊销").trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(updateUser).toHaveBeenCalledWith(1, expect.objectContaining({ status: "REVOKED" }));
    expect(showToast).toHaveBeenCalledWith("success", "已吊销");
    expect(pageUsers).toHaveBeenCalledTimes(2);
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(false);
  });

  it("吊销失败时确认框留着，可以直接重试", async () => {
    updateUser.mockRejectedValueOnce(new Error("timeout"));
    const wrapper = await render();
    await action(wrapper, 0, "吊销").trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "吊销失败：timeout");
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(true);
  });
});

describe("UsersView 删除", () => {
  it("确认框说清连带影响：订阅与落地出口会随之释放", async () => {
    const wrapper = await render();

    await action(wrapper, 0, "删除").trigger("click");

    const message = String(wrapper.findComponent(ConfirmDialog).props("message"));
    expect(message).toContain("a@acme.com");
    expect(message).toContain("释放");
  });

  it("确认后删除并刷新列表", async () => {
    const wrapper = await render();
    await action(wrapper, 0, "删除").trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deleteUser).toHaveBeenCalledWith(1);
    expect(pageUsers).toHaveBeenCalledTimes(2);
  });
});

describe("UsersView 搜索与筛选", () => {
  it("搜索时去掉前后空白再发出去", async () => {
    const wrapper = await render();

    await wrapper.get(".admin-input.search").setValue("  zhang@acme.com  ");
    await wrapper.get(".admin-toolbar .admin-btn-ghost").trigger("click");
    await flushPromises();

    expect(pageUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: "zhang@acme.com" }),
    );
  });

  it("搜索框写明备注也能搜——不写的话没人会想到拿备注找人", async () => {
    const wrapper = await render();

    expect(wrapper.get(".admin-input.search").attributes("placeholder")).toContain("备注");
  });

  it("回车也能搜", async () => {
    const wrapper = await render();

    await wrapper.get(".admin-input.search").setValue("acme");
    await wrapper.get(".admin-input.search").trigger("keyup.enter");
    await flushPromises();

    expect(pageUsers).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: "acme" }));
  });

  it("换筛选条件时回到第一页，不停在原来那页", async () => {
    pageUsers.mockResolvedValue(page([user()], 100));
    const wrapper = await render();

    await wrapper.findAll(".admin-pager .admin-btn-ghost")[1].trigger("click");
    await flushPromises();
    expect(pageUsers).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2 }));

    wrapper.findAllComponents({ name: "AdminSelect" })[0].vm.$emit("update:modelValue", true);
    await flushPromises();

    expect(pageUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNo: 1, hasActiveSubscription: true }),
    );
  });
});

describe("UsersView 空态", () => {
  it("一个人都没有时说清用户是自动建档的，不给「新建」这种做不到的动作", async () => {
    pageUsers.mockResolvedValue(page([]));
    const wrapper = await render();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("自动建档");
    // 工具栏的「搜索」也是 ghost 按钮，故按文案判定空态动作区
    expect(wrapper.findAll(".admin-btn-ghost").some((b) => b.text() === "清除筛选")).toBe(false);
  });

  it("筛选后没匹配上是另一回事，给「清除筛选」", async () => {
    const wrapper = await render();
    await wrapper.get(".admin-input.search").setValue("nobody");
    pageUsers.mockResolvedValue(page([]));
    await wrapper.get(".admin-toolbar .admin-btn-ghost").trigger("click");
    await flushPromises();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("换个关键词");

    const clear = wrapper.findAll(".admin-btn-ghost").find((b) => b.text() === "清除筛选")!;
    await clear.trigger("click");
    await flushPromises();

    expect(pageUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: "", hasActiveSubscription: null }),
    );
  });

  it("空态说法看的是「已生效的筛选」，不是输入框里正在打的字", async () => {
    pageUsers.mockResolvedValue(page([]));
    const wrapper = await render();

    // 只打字、没点搜索：列表还是「一个人都没有」的那套说法
    await wrapper.get(".admin-input.search").setValue("还没搜呢");
    await wrapper.vm.$nextTick();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("自动建档");
  });
});

describe("UsersView 分页", () => {
  it("一页都翻不动时不摆分页器", async () => {
    pageUsers.mockResolvedValue(page([user()], 1));
    const wrapper = await render();

    expect(wrapper.find(".admin-pager").exists()).toBe(true);
    expect(
      wrapper.findAll(".admin-pager .admin-btn-ghost")[0].attributes("disabled"),
    ).toBeDefined();
  });

  it("一条都没有时连分页器都不出", async () => {
    pageUsers.mockResolvedValue(page([], 0));
    const wrapper = await render();

    expect(wrapper.find(".admin-pager").exists()).toBe(false);
  });

  it("末页时下一页禁用，不会翻出界", async () => {
    pageUsers.mockResolvedValue(page([user()], 15));
    const wrapper = await render();

    const [prev, next] = wrapper.findAll(".admin-pager .admin-btn-ghost");
    expect(prev.attributes("disabled")).toBeDefined();
    expect(next.attributes("disabled")).toBeDefined();
    expect(wrapper.get(".admin-pager .info").text()).toContain("第 1 / 1 页");
  });

  it("改每页条数时回到第一页重新拉", async () => {
    pageUsers.mockResolvedValue(page([user()], 100));
    const wrapper = await render();

    const sizeSelect = wrapper.findAllComponents({ name: "AdminSelect" }).at(-1)!;
    sizeSelect.vm.$emit("update:modelValue", 50);
    await flushPromises();

    expect(pageUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 50 }),
    );
  });
});

describe("UsersView 表格内容", () => {
  it("在期订阅逐个列成标签，带到期日", async () => {
    pageUsers.mockResolvedValue(
      page([
        user({
          activeSubscriptions: [
            { id: 1, agentType: "CLAUDE", endsAt: "2026-12-31T00:00:00Z" },
            { id: 2, agentType: "CODEX", endsAt: "2026-11-30T00:00:00Z" },
          ],
        } as Partial<AdminUserResponse>),
      ]),
    );
    const wrapper = await render();

    const pills = wrapper.findAll("tbody tr")[0].findAll(".pill");
    expect(pills).toHaveLength(2);
    expect(pills[0].text()).toContain("Claude Code");
  });

  it("没有在期订阅时说「无」，落地节点没分配时说「未分配」", async () => {
    const wrapper = await render();

    const row = wrapper.findAll("tbody tr")[0];
    expect(row.findAll("td")[5].text()).toBe("未分配");
    expect(row.findAll("td")[7].text()).toBe("无");
  });

  it("备注列照仓里惯例排在「更新时间」前一格", async () => {
    pageUsers.mockResolvedValue(page([user({ remark: "试用期" })]));
    const wrapper = await render();

    const headers = wrapper.findAll("thead th").map((th) => th.text());
    expect(headers[8]).toBe("备注");
    expect(headers[9]).toBe("更新时间");
    expect(wrapper.findAll("tbody tr")[0].findAll("td")[8].text()).toBe("试用期");
  });

  it("没写备注时显示占位符，不留一格空白让人以为漏渲染了", async () => {
    const wrapper = await render();

    expect(wrapper.findAll("tbody tr")[0].findAll("td")[8].text()).toBe("—");
  });

  it("备注交给 TruncatedText，不把整张表撑成无限长，原文靠悬浮看全", async () => {
    const long = "很".repeat(50);
    pageUsers.mockResolvedValue(page([user({ remark: long })]));
    const wrapper = await render();

    const cell = wrapper.findAll("tbody tr")[0].findAll("td")[8];
    expect(cell.findComponent(TruncatedText).props("text")).toBe(long);
  });

  it("截断的是备注本身，模板换行不该在单元格里渲染出多余空白", async () => {
    pageUsers.mockResolvedValue(page([user({ remark: "老客户" })]));
    const wrapper = await render();

    const cell = wrapper.findAll("tbody tr")[0].findAll("td")[8].find(".truncated");
    expect(cell.element.textContent).toBe("老客户");
  });
});
