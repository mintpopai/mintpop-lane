import { describe, expect, it, vi } from "vitest";
import { createAdminApi } from "./admin";
import type { HttpClient } from "./http";
import type { NodeSaveRequest } from "./types";

function 假客户端() {
  const request = vi.fn(async () => null as never);
  return { http: { request } as unknown as HttpClient, request };
}

describe("createAdminApi", () => {
  it("分页查询把关键字与页码拼成查询串", async () => {
    const { http, request } = 假客户端();

    await createAdminApi(http).pageUsers({
      keyword: "张",
      hasActiveSubscription: null,
      pageNo: 2,
      pageSize: 20,
    });

    expect(request).toHaveBeenCalledWith("/admin/users?keyword=%E5%BC%A0&pageNo=2&pageSize=20");
  });

  it("关键字为空时不发 keyword 参数，避免服务端按空串去 like", async () => {
    const { http, request } = 假客户端();

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
    const { http, request } = 假客户端();
    const api = createAdminApi(http);

    await api.listNodes("LAND");
    await api.listNodes();

    expect(request).toHaveBeenNthCalledWith(1, "/admin/nodes?role=LAND");
    expect(request).toHaveBeenNthCalledWith(2, "/admin/nodes");
  });

  it("新增与更新用 POST / PUT 并把 body 序列化成 JSON", async () => {
    const { http, request } = 假客户端();
    const api = createAdminApi(http);
    // 显式标注成 NodeSaveRequest 而不是 as const：as const 会把 egressIps 变成
    // readonly []，展开后与 string[] 不兼容；标注类型还顺带让这条测试校验载荷形状
    const body: NodeSaveRequest = {
      name: "US-01",
      role: "FRONT",
      protocol: "TROJAN",
      serverAddr: "us.example.com",
      port: 443,
      extraConfig: {},
      secret: {},
      egressIps: [],
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
});
