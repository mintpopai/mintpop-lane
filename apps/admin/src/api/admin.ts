import type { HttpClient } from "./http";
import type {
  AdminNodeResponse,
  AdminUserResponse,
  NodeRole,
  NodeSaveRequest,
  PageResult,
  UserPageQuery,
  UserSaveRequest,
} from "./types";

export interface AdminApi {
  pageUsers(query: UserPageQuery): Promise<PageResult<AdminUserResponse>>;
  createUser(body: UserSaveRequest): Promise<number>;
  updateUser(id: number, body: UserSaveRequest): Promise<void>;
  deleteUser(id: number): Promise<void>;
  listNodes(role?: NodeRole): Promise<AdminNodeResponse[]>;
  createNode(body: NodeSaveRequest): Promise<number>;
  updateNode(id: number, body: NodeSaveRequest): Promise<void>;
  deleteNode(id: number): Promise<void>;
}

/** 管理接口的薄封装。http 由外部传入，测试里换成假的即可 */
export function createAdminApi(http: HttpClient): AdminApi {
  return {
    pageUsers(query) {
      const params = new URLSearchParams();
      // 关键字为空就不发这个参数：服务端拿到空串会去做一次没有意义的 like
      if (query.keyword) {
        params.set("keyword", query.keyword);
      }
      params.set("pageNo", String(query.pageNo));
      params.set("pageSize", String(query.pageSize));
      return http.request(`/admin/users?${params.toString()}`);
    },

    createUser(body) {
      return http.request("/admin/users", { method: "POST", body: JSON.stringify(body) });
    },

    updateUser(id, body) {
      return http.request(`/admin/users/${id}`, { method: "PUT", body: JSON.stringify(body) });
    },

    deleteUser(id) {
      return http.request(`/admin/users/${id}`, { method: "DELETE" });
    },

    listNodes(role) {
      return http.request(role ? `/admin/nodes?role=${role}` : "/admin/nodes");
    },

    createNode(body) {
      return http.request("/admin/nodes", { method: "POST", body: JSON.stringify(body) });
    },

    updateNode(id, body) {
      return http.request(`/admin/nodes/${id}`, { method: "PUT", body: JSON.stringify(body) });
    },

    deleteNode(id) {
      return http.request(`/admin/nodes/${id}`, { method: "DELETE" });
    },
  };
}
