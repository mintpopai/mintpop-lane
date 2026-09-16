import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { PlanResponse, PlanSaveRequest } from "../api/types";
import { showToast } from "../toast";
import PlanFormModal from "./PlanFormModal.vue";

const createPlan = vi.fn<(body: PlanSaveRequest) => Promise<void>>(async () => undefined);
const updatePlan = vi.fn<(id: number, body: PlanSaveRequest) => Promise<void>>(
  async () => undefined,
);

vi.mock("../api", () => ({ adminApi: () => ({ createPlan, updatePlan }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function plan(overrides: Partial<PlanResponse> = {}): PlanResponse {
  return {
    id: 5,
    name: "月付套餐",
    agentType: "CLAUDE",
    durationDays: 30,
    price: 29.9,
    currency: "USD",
    description: "含 5 个并发席位",
    imageUrl: "https://assets.lane.mintpop.ai/plans/2026/09/a.png",
    enabled: true,
    remark: "老客专享",
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
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

function render(editing: PlanResponse | null) {
  return mount(PlanFormModal, { attachTo: document.body, props: { editing } });
}

async function submit(wrapper: ReturnType<typeof render>) {
  await query<HTMLButtonElement>(".admin-btn").trigger("click");
  await wrapper.vm.$nextTick();
}

describe("PlanFormModal 打开时", () => {
  it("新建时是一张空表，标题说明这是新建", () => {
    render(null);

    expect(query(".dialog").attributes("aria-label")).toBe("新建套餐");
    expect(query<HTMLInputElement>("#plan-name").element.value).toBe("");
    expect(query<HTMLInputElement>("#plan-duration").element.value).toBe("");
  });

  it("编辑时回填原值，标题带上是哪个套餐", () => {
    render(plan());

    expect(query(".dialog").attributes("aria-label")).toBe("编辑套餐：月付套餐");
    expect(query<HTMLInputElement>("#plan-name").element.value).toBe("月付套餐");
    expect(query<HTMLInputElement>("#plan-duration").element.value).toBe("30");
    expect(query<HTMLInputElement>("#plan-price").element.value).toBe("29.9");
    expect(query<HTMLInputElement>("#plan-remark").element.value).toBe("老客专享");
  });

  it("备注为空的套餐回填成空串，不显示 null", () => {
    render(plan({ remark: null }));

    expect(query<HTMLInputElement>("#plan-remark").element.value).toBe("");
  });
});

describe("PlanFormModal 提交校验", () => {
  it("校验不过时只提示第一条，且一个请求都不发", async () => {
    const wrapper = render(null);

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "套餐名不能为空");
    expect(createPlan).not.toHaveBeenCalled();
  });

  it("时长不是正整数时挡下", async () => {
    const wrapper = render(plan({ durationDays: 0 }));

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "时长必须是不小于 1 的整数（天）");
    expect(updatePlan).not.toHaveBeenCalled();
  });

  it("价格超过两位小数时挡下", async () => {
    const wrapper = render(plan({ price: 29.999 }));

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "价格必须是不小于 0 的数，至多两位小数");
    expect(updatePlan).not.toHaveBeenCalled();
  });
});

describe("PlanFormModal 保存", () => {
  it("新建走 createPlan，名称与备注前后空白被抹掉", async () => {
    const wrapper = render(null);
    await query<HTMLInputElement>("#plan-name").setValue("  季付套餐  ");
    await query<HTMLInputElement>("#plan-duration").setValue("90");
    await query<HTMLInputElement>("#plan-price").setValue("79.9");

    await submit(wrapper);

    expect(createPlan).toHaveBeenCalledWith({
      name: "季付套餐",
      agentType: "CLAUDE",
      durationDays: 90,
      price: 79.9,
      currency: "USD",
      description: "",
      imageUrl: "",
      enabled: true,
      remark: "",
    });
  });

  it("编辑走 updatePlan，并带上这条记录的 id", async () => {
    const wrapper = render(plan());

    await submit(wrapper);

    expect(updatePlan).toHaveBeenCalledWith(5, expect.objectContaining({ name: "月付套餐" }));
    expect(createPlan).not.toHaveBeenCalled();
  });

  it("保存成功后告诉父组件「存好了」并让它关掉弹窗", async () => {
    const wrapper = render(plan());

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("success", "已保存");
    expect(wrapper.emitted("saved")).toHaveLength(1);
    expect(wrapper.emitted("close")).toHaveLength(1);
  });

  it("撞上业务错误（如套餐名重复）时，把服务端那句中文原样提示", async () => {
    updatePlan.mockRejectedValueOnce(new BizError(410018, "套餐名已存在"));
    const wrapper = render(plan());

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "套餐名已存在");
    // 没存成就别关，让人能改了再存
    expect(wrapper.emitted("saved")).toBeUndefined();
    expect(wrapper.emitted("close")).toBeUndefined();
  });

  it("网络类异常没有中文可用，兜底成「保存失败：…」", async () => {
    updatePlan.mockRejectedValueOnce(new Error("Failed to fetch"));
    const wrapper = render(plan());

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "保存失败：Failed to fetch");
  });

  it("保存中按钮禁用并改字，防止连点存两次", async () => {
    let release!: () => void;
    updatePlan.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        release = resolve;
      }),
    );
    const wrapper = render(plan());

    await query<HTMLButtonElement>(".admin-btn").trigger("click");
    await wrapper.vm.$nextTick();
    expect(query<HTMLButtonElement>(".admin-btn").element.disabled).toBe(true);
    expect(query(".admin-btn").text()).toBe("保存中…");

    release();
    await wrapper.vm.$nextTick();
    await wrapper.vm.$nextTick();
    expect(query<HTMLButtonElement>(".admin-btn").element.disabled).toBe(false);
  });

  it("取消只是关掉，不碰任何接口", async () => {
    const wrapper = render(plan());

    await query<HTMLButtonElement>(".admin-btn-ghost").trigger("click");

    expect(wrapper.emitted("close")).toHaveLength(1);
    expect(updatePlan).not.toHaveBeenCalled();
  });
});
