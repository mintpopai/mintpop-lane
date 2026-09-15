import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { EnterpriseResponse, EnterpriseSaveRequest } from "../api/types";
import { showToast } from "../toast";
import EnterpriseFormModal from "./EnterpriseFormModal.vue";

const createEnterprise = vi.fn<(body: EnterpriseSaveRequest) => Promise<void>>(
  async () => undefined,
);
const updateEnterprise = vi.fn<(id: number, body: EnterpriseSaveRequest) => Promise<void>>(
  async () => undefined,
);

vi.mock("../api", () => ({ adminApi: () => ({ createEnterprise, updateEnterprise }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function enterprise(overrides: Partial<EnterpriseResponse> = {}): EnterpriseResponse {
  return {
    id: 8,
    name: "Acme 科技",
    domain: "acme.com",
    agentTypes: ["CLAUDE"],
    enabled: true,
    remark: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
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

function chips(): DOMWrapper<HTMLButtonElement>[] {
  return [...document.querySelectorAll<HTMLButtonElement>(".agent-chip")].map(
    (el) => new DOMWrapper(el),
  );
}

function render(editing: EnterpriseResponse | null) {
  return mount(EnterpriseFormModal, { attachTo: document.body, props: { editing } });
}

async function submit(wrapper: ReturnType<typeof render>) {
  await query<HTMLButtonElement>(".admin-btn").trigger("click");
  await wrapper.vm.$nextTick();
}

describe("EnterpriseFormModal 打开时", () => {
  it("新建时是一张空表，一个 Agent 类型都没勾", () => {
    render(null);

    expect(query(".dialog").attributes("aria-label")).toBe("新建企业");
    expect(query<HTMLInputElement>("#enterprise-name").element.value).toBe("");
    expect(chips().every((c) => c.attributes("aria-pressed") === "false")).toBe(true);
  });

  it("编辑时回填原值，已支持的类型呈勾中态", () => {
    render(enterprise({ agentTypes: ["CODEX"] }));

    expect(query(".dialog").attributes("aria-label")).toBe("编辑企业：Acme 科技");
    expect(query<HTMLInputElement>("#enterprise-domain").element.value).toBe("acme.com");
    const pressed = chips().filter((c) => c.attributes("aria-pressed") === "true");
    expect(pressed).toHaveLength(1);
    expect(pressed[0].text()).toContain("Codex");
  });

  it("服务端新增了本前端不认识的类型时照样列出来，编辑老记录不把它悄悄丢掉", () => {
    render(enterprise({ agentTypes: ["CLAUDE", "GEMINI"] }));

    const labels = chips().map((c) => c.text().replace("✓", "").trim());
    expect(labels).toContain("GEMINI");
    // 未知类型默认是勾中的（它本来就在这条记录上）
    const gemini = chips().find((c) => c.text().includes("GEMINI"))!;
    expect(gemini.attributes("aria-pressed")).toBe("true");
  });

  it("勾选状态挂在 aria-pressed 上，不是只靠颜色", async () => {
    render(null);
    const claude = chips()[0];
    expect(claude.attributes("aria-pressed")).toBe("false");

    await claude.trigger("click");
    expect(chips()[0].attributes("aria-pressed")).toBe("true");
    expect(chips()[0].classes()).toContain("selected");

    await chips()[0].trigger("click");
    expect(chips()[0].attributes("aria-pressed")).toBe("false");
  });

  it("这组 chip 对读屏是一组，且说明了这组在选什么", () => {
    render(null);

    const group = query(".agent-choices");
    expect(group.attributes("role")).toBe("group");
    expect(group.attributes("aria-labelledby")).toBe("enterprise-agent-types");
  });
});

describe("EnterpriseFormModal 提交校验", () => {
  it("名称为空时挡下，不发请求", async () => {
    const wrapper = render(null);

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "企业名称不能为空");
    expect(createEnterprise).not.toHaveBeenCalled();
  });

  it("域名带协议或路径时挡下，只收裸域名", async () => {
    const wrapper = render(enterprise({ domain: "https://acme.com/login" }));

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "企业域名格式不对，形如 acme.com");
    expect(updateEnterprise).not.toHaveBeenCalled();
  });

  it("一个 Agent 类型都没勾时挡下——那样这家企业分不了任何订阅", async () => {
    const wrapper = render(enterprise({ agentTypes: [] }));

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "请至少选择一个 Agent 类型");
    expect(updateEnterprise).not.toHaveBeenCalled();
  });
});

describe("EnterpriseFormModal 保存", () => {
  it("域名统一转小写提交，与最终落库形态一致", async () => {
    const wrapper = render(enterprise({ domain: "ACME.COM" }));

    await submit(wrapper);

    expect(updateEnterprise).toHaveBeenCalledWith(
      8,
      expect.objectContaining({ domain: "acme.com" }),
    );
  });

  it("新建走 createEnterprise，名称前后空白被抹掉", async () => {
    const wrapper = render(null);
    await query<HTMLInputElement>("#enterprise-name").setValue("  Beta 实验室  ");
    await query<HTMLInputElement>("#enterprise-domain").setValue("beta.io");
    await chips()[0].trigger("click");

    await submit(wrapper);

    expect(createEnterprise).toHaveBeenCalledWith({
      name: "Beta 实验室",
      domain: "beta.io",
      agentTypes: ["CLAUDE"],
      enabled: true,
      remark: "",
    });
  });

  it("保存成功后通知父组件并关掉弹窗", async () => {
    const wrapper = render(enterprise());

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("success", "已保存");
    expect(wrapper.emitted("saved")).toHaveLength(1);
    expect(wrapper.emitted("close")).toHaveLength(1);
  });

  it("撞上域名重复这类业务错误时，用服务端那句中文，且不关弹窗", async () => {
    updateEnterprise.mockRejectedValueOnce(new BizError(410022, "域名已存在"));
    const wrapper = render(enterprise());

    await submit(wrapper);

    expect(showToast).toHaveBeenCalledWith("error", "域名已存在");
    expect(wrapper.emitted("close")).toBeUndefined();
  });

  it("勾选改动不回写到列表数据上", async () => {
    const record = enterprise({ agentTypes: ["CLAUDE"] });
    render(record);

    await chips()[1].trigger("click");

    expect(record.agentTypes).toEqual(["CLAUDE"]);
  });
});
