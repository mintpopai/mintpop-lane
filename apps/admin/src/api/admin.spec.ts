import { describe, expect, it, vi } from "vitest";
import { createAdminApi } from "./admin";
import type { HttpClient } from "./http";
import type { NodeSaveRequest } from "./types";

function fakeClient() {
  const request = vi.fn(async () => null as never);
  return { http: { request } as unknown as HttpClient, request };
}

describe("createAdminApi", () => {
  it("分页查询把关键字与页码拼成查询串", async () => {
    const { http, request } = fakeClient();

    await createAdminApi(http).pageUsers({
      keyword: "张",
      hasActiveSubscription: null,
      pageNo: 2,
      pageSize: 20,
    });

    expect(request).toHaveBeenCalledWith("/admin/users?keyword=%E5%BC%A0&pageNo=2&pageSize=20");
  });

  it("关键字为空时不发 keyword 参数，避免服务端按空串去 like", async () => {
    const { http, request } = fakeClient();

    await createAdminApi(http).pageUsers({
      keyword: "",
      hasActiveSubscription: null,
      pageNo: 1,
      pageSize: 20,
    });

    expect(request).toHaveBeenCalledWith("/admin/users?pageNo=1&pageSize=20");
  });

  it("pageUsers 带在期订阅筛选，null 时不发该参数", async () => {
    const paths: string[] = [];
    const http: HttpClient = {
      request: async <T>(path: string) => {
        paths.push(path);
        return { records: [], total: 0, pageNo: 1, pageSize: 20 } as T;
      },
    };
    const api = createAdminApi(http);
    await api.pageUsers({ keyword: "", hasActiveSubscription: true, pageNo: 1, pageSize: 20 });
    await api.pageUsers({ keyword: "", hasActiveSubscription: null, pageNo: 1, pageSize: 20 });
    expect(paths[0]).toContain("hasActiveSubscription=true");
    expect(paths[1]).not.toContain("hasActiveSubscription");
  });

  it("节点列表按角色过滤；不传角色就取全部", async () => {
    const { http, request } = fakeClient();
    const api = createAdminApi(http);

    await api.listNodes("LAND");
    await api.listNodes();

    expect(request).toHaveBeenNthCalledWith(1, "/admin/nodes?role=LAND");
    expect(request).toHaveBeenNthCalledWith(2, "/admin/nodes");
  });

  it("新增与更新用 POST / PUT 并把 body 序列化成 JSON", async () => {
    const { http, request } = fakeClient();
    const api = createAdminApi(http);
    // 显式标注成 NodeSaveRequest，让这条测试顺带校验载荷形状
    const body: NodeSaveRequest = {
      name: "US-01",
      role: "FRONT",
      protocol: "TROJAN",
      serverAddr: "us.example.com",
      port: 443,
      extraConfig: {},
      secret: {},
      egressTimezone: null,
      egressIp: null,
      capacity: null,
      status: "ENABLED",
      remark: "",
    };

    await api.createNode({ ...body });
    await api.updateNode(3, { ...body });
    await api.deleteNode(3);

    expect(request).toHaveBeenNthCalledWith(1, "/admin/nodes", {
      method: "POST",
      body: JSON.stringify(body),
    });
    expect(request).toHaveBeenNthCalledWith(2, "/admin/nodes/3", {
      method: "PUT",
      body: JSON.stringify(body),
    });
    expect(request).toHaveBeenNthCalledWith(3, "/admin/nodes/3", { method: "DELETE" });
  });

  it("订阅四端点路径与方法正确", async () => {
    const calls: Array<{ path: string; method?: string }> = [];
    const http: HttpClient = {
      request: async <T>(path: string, init?: RequestInit) => {
        calls.push({ path, method: init?.method });
        return [] as T;
      },
    };
    const api = createAdminApi(http);
    const body = {
      agentType: "CLAUDE" as const,
      name: "Claude 席位 1",
      startsAt: "2026-08-01T00:00:00",
      endsAt: "2026-09-01T00:00:00",
      credential: "sk-ant-x",
      remark: "",
    };
    await api.listSubscriptions(7);
    await api.createSubscription(7, body);
    await api.updateSubscription(3, body);
    await api.deleteSubscription(3);
    expect(calls).toEqual([
      { path: "/admin/users/7/subscriptions", method: undefined },
      { path: "/admin/users/7/subscriptions", method: "POST" },
      { path: "/admin/subscriptions/3", method: "PUT" },
      { path: "/admin/subscriptions/3", method: "DELETE" },
    ]);
  });

  it("分组接口逐个打到正确的路径与方法", async () => {
    const { http, request } = fakeClient();
    const api = createAdminApi(http);

    await api.previewSub({ subUrl: "https://sub.example.com/c?token=t" });
    await api.createNodeGroup({ name: "机场A", subUrl: "https://sub.example.com/c?token=t", selectedNames: ["香港-01"], remark: "" });
    await api.listNodeGroups();
    await api.renameNodeGroup(3, { name: "机场A-新名", remark: "" });
    await api.refreshPreviewNodeGroup(3);
    await api.importNodeGroup(3, { selectedNames: ["新加坡-01"] });
    await api.deleteNodeGroup(3);

    expect(request).toHaveBeenNthCalledWith(1, "/admin/node-groups/preview", {
      method: "POST",
      body: JSON.stringify({ subUrl: "https://sub.example.com/c?token=t" }),
    });
    expect(request).toHaveBeenNthCalledWith(2, "/admin/node-groups", expect.objectContaining({ method: "POST" }));
    expect(request).toHaveBeenNthCalledWith(3, "/admin/node-groups");
    expect(request).toHaveBeenNthCalledWith(4, "/admin/node-groups/3", expect.objectContaining({ method: "PUT" }));
    expect(request).toHaveBeenNthCalledWith(5, "/admin/node-groups/3/refresh-preview", { method: "POST" });
    expect(request).toHaveBeenNthCalledWith(6, "/admin/node-groups/3/import", {
      method: "POST",
      body: JSON.stringify({ selectedNames: ["新加坡-01"] }),
    });
    expect(request).toHaveBeenNthCalledWith(7, "/admin/node-groups/3", { method: "DELETE" });
  });
});
