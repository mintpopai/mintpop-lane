import type { HttpClient } from "./http";
import type {
  AdminNodeResponse,
  AdminSubscriptionResponse,
  AdminUserResponse,
  NodeGroupCreateRequest,
  NodeGroupImportRequest,
  NodeGroupRenameRequest,
  NodeGroupResponse,
  NodeRole,
  NodeSaveRequest,
  PageResult,
  PlanResponse,
  PlanSaveRequest,
  SubPreviewNode,
  SubPreviewRequest,
  SubscriptionSaveRequest,
  UserPageQuery,
  UserSaveRequest,
} from "./types";

export interface AdminApi {
  pageUsers(query: UserPageQuery): Promise<PageResult<AdminUserResponse>>;
  updateUser(id: number, body: UserSaveRequest): Promise<void>;
  deleteUser(id: number): Promise<void>;
  listNodes(role?: NodeRole): Promise<AdminNodeResponse[]>;
  createNode(body: NodeSaveRequest): Promise<number>;
  updateNode(id: number, body: NodeSaveRequest): Promise<void>;
  deleteNode(id: number): Promise<void>;
  listSubscriptions(userId: number): Promise<AdminSubscriptionResponse[]>;
  createSubscription(userId: number, body: SubscriptionSaveRequest): Promise<number>;
  updateSubscription(id: number, body: SubscriptionSaveRequest): Promise<void>;
  deleteSubscription(id: number): Promise<void>;
  previewSub(body: SubPreviewRequest): Promise<SubPreviewNode[]>;
  createNodeGroup(body: NodeGroupCreateRequest): Promise<number>;
  listNodeGroups(): Promise<NodeGroupResponse[]>;
  renameNodeGroup(id: number, body: NodeGroupRenameRequest): Promise<void>;
  refreshPreviewNodeGroup(id: number): Promise<SubPreviewNode[]>;
  importNodeGroup(id: number, body: NodeGroupImportRequest): Promise<void>;
  deleteNodeGroup(id: number): Promise<void>;
  listPlans(): Promise<PlanResponse[]>;
  createPlan(body: PlanSaveRequest): Promise<number>;
  updatePlan(id: number, body: PlanSaveRequest): Promise<void>;
  deletePlan(id: number): Promise<void>;
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
      if (query.hasActiveSubscription !== null) {
        params.set("hasActiveSubscription", String(query.hasActiveSubscription));
      }
      params.set("pageNo", String(query.pageNo));
      params.set("pageSize", String(query.pageSize));
      return http.request(`/admin/users?${params.toString()}`);
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

    listSubscriptions(userId) {
      return http.request(`/admin/users/${userId}/subscriptions`);
    },

    createSubscription(userId, body) {
      return http.request(`/admin/users/${userId}/subscriptions`, {
        method: "POST",
        body: JSON.stringify(body),
      });
    },

    updateSubscription(id, body) {
      return http.request(`/admin/subscriptions/${id}`, { method: "PUT", body: JSON.stringify(body) });
    },

    deleteSubscription(id) {
      return http.request(`/admin/subscriptions/${id}`, { method: "DELETE" });
    },

    previewSub(body) {
      return http.request("/admin/node-groups/preview", { method: "POST", body: JSON.stringify(body) });
    },

    createNodeGroup(body) {
      return http.request("/admin/node-groups", { method: "POST", body: JSON.stringify(body) });
    },

    listNodeGroups() {
      return http.request("/admin/node-groups");
    },

    renameNodeGroup(id, body) {
      return http.request(`/admin/node-groups/${id}`, { method: "PUT", body: JSON.stringify(body) });
    },

    refreshPreviewNodeGroup(id) {
      return http.request(`/admin/node-groups/${id}/refresh-preview`, { method: "POST" });
    },

    importNodeGroup(id, body) {
      return http.request(`/admin/node-groups/${id}/import`, { method: "POST", body: JSON.stringify(body) });
    },

    deleteNodeGroup(id) {
      return http.request(`/admin/node-groups/${id}`, { method: "DELETE" });
    },

    listPlans() {
      return http.request("/admin/plans");
    },

    createPlan(body) {
      return http.request("/admin/plans", { method: "POST", body: JSON.stringify(body) });
    },

    updatePlan(id, body) {
      return http.request(`/admin/plans/${id}`, { method: "PUT", body: JSON.stringify(body) });
    },

    deletePlan(id) {
      return http.request(`/admin/plans/${id}`, { method: "DELETE" });
    },
  };
}
