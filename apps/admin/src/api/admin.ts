import type { HttpClient } from "./http";
import type {
  AdminDeviceRebindRequestResponse,
  AdminNodeResponse,
  AdminSubscriptionResponse,
  AdminUserResponse,
  CredentialAuthorizationStart,
  CredentialExchangeRequest,
  CredentialIssueResult,
  CredentialRevokeResult,
  NodeGroupCreateRequest,
  NodeGroupImportRequest,
  NodeGroupRenameRequest,
  NodeGroupResponse,
  NodeProbeResponse,
  NodeRole,
  NodeSaveRequest,
  PageResult,
  PlanResponse,
  PlanSaveRequest,
  RebindRequestStatus,
  SubAuditRequest,
  SubAuditResponse,
  SubPreviewNode,
  SubPreviewRequest,
  SubscriptionCreateRequest,
  SubscriptionUpdateRequest,
  UserPageQuery,
  UserSaveRequest,
  EnterpriseResponse,
  EnterpriseSaveRequest,
  ImageUploadResponse,
  LinkHealthResponse,
} from "./types";

export interface AdminApi {
  pageUsers(query: UserPageQuery): Promise<PageResult<AdminUserResponse>>;
  getUser(id: number): Promise<AdminUserResponse>;
  updateUser(id: number, body: UserSaveRequest): Promise<void>;
  deleteUser(id: number): Promise<void>;
  listNodes(role?: NodeRole): Promise<AdminNodeResponse[]>;
  createNode(body: NodeSaveRequest): Promise<number>;
  updateNode(id: number, body: NodeSaveRequest): Promise<void>;
  deleteNode(id: number): Promise<void>;
  /** 经落地节点探测连通性与实际出口 IP；只对 LAND 节点开放 */
  probeNode(id: number): Promise<NodeProbeResponse>;
  listSubscriptions(userId: number): Promise<AdminSubscriptionResponse[]>;
  createSubscription(userId: number, body: SubscriptionCreateRequest): Promise<number>;
  updateSubscription(id: number, body: SubscriptionUpdateRequest): Promise<void>;
  deleteSubscription(id: number): Promise<void>;
  credentialAuthorizeUrl(subscriptionId: number): Promise<CredentialAuthorizationStart>;
  credentialExchange(
    subscriptionId: number,
    body: CredentialExchangeRequest,
  ): Promise<CredentialIssueResult>;
  credentialRevoke(subscriptionId: number): Promise<CredentialRevokeResult>;
  /** 换机申请列表；不传状态即全部历史，按提交时间倒序 */
  listDeviceRebindRequests(
    status?: RebindRequestStatus,
  ): Promise<AdminDeviceRebindRequestResponse[]>;
  approveDeviceRebindRequest(id: number): Promise<void>;
  rejectDeviceRebindRequest(id: number): Promise<void>;
  /** 强制解绑订阅当前绑定的设备。与有没有申请无关，故挂在订阅下 */
  unbindSubscriptionDevice(id: number): Promise<void>;
  previewSub(body: SubPreviewRequest): Promise<SubPreviewNode[]>;
  /** 采购尽调：候选机场的试用订阅是否与库里已有节点撞故障域。只读，不落库 */
  auditNodeGroup(body: SubAuditRequest): Promise<SubAuditResponse>;
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
  /** 上传图片，返回公开 URL；套餐主图与富文本插图共用 */
  uploadImage(file: File): Promise<string>;
  listEnterprises(): Promise<EnterpriseResponse[]>;
  createEnterprise(body: EnterpriseSaveRequest): Promise<number>;
  updateEnterprise(id: number, body: EnterpriseSaveRequest): Promise<void>;
  deleteEnterprise(id: number): Promise<void>;
  /** 故障域 × 运营商成功率矩阵 + 入口 IP 变更时间线；不传天数时服务端按 7 天算 */
  getLinkHealth(days?: number): Promise<LinkHealthResponse>;
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

    getUser(id) {
      return http.request(`/admin/users/${id}`);
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
    probeNode(id) {
      return http.request(`/admin/nodes/${id}/probe`, { method: "POST" });
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
      return http.request(`/admin/subscriptions/${id}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
    },

    deleteSubscription(id) {
      return http.request(`/admin/subscriptions/${id}`, { method: "DELETE" });
    },

    credentialAuthorizeUrl(subscriptionId) {
      return http.request(`/admin/subscriptions/${subscriptionId}/credential/authorize-url`, {
        method: "POST",
      });
    },

    credentialExchange(subscriptionId, body) {
      return http.request(`/admin/subscriptions/${subscriptionId}/credential/exchange`, {
        method: "POST",
        body: JSON.stringify(body),
      });
    },

    credentialRevoke(subscriptionId) {
      return http.request(`/admin/subscriptions/${subscriptionId}/credential/revoke`, {
        method: "POST",
      });
    },

    listDeviceRebindRequests(status) {
      return http.request(
        status ? `/admin/device-rebind-requests?status=${status}` : "/admin/device-rebind-requests",
      );
    },

    approveDeviceRebindRequest(id) {
      return http.request(`/admin/device-rebind-requests/${id}/approve`, { method: "POST" });
    },

    rejectDeviceRebindRequest(id) {
      return http.request(`/admin/device-rebind-requests/${id}/reject`, { method: "POST" });
    },

    unbindSubscriptionDevice(id) {
      return http.request(`/admin/subscriptions/${id}/device/unbind`, { method: "POST" });
    },

    previewSub(body) {
      return http.request("/admin/node-groups/preview", {
        method: "POST",
        body: JSON.stringify(body),
      });
    },

    auditNodeGroup(body) {
      return http.request("/admin/node-groups/audit", {
        method: "POST",
        body: JSON.stringify(body),
      });
    },

    createNodeGroup(body) {
      return http.request("/admin/node-groups", { method: "POST", body: JSON.stringify(body) });
    },

    listNodeGroups() {
      return http.request("/admin/node-groups");
    },

    renameNodeGroup(id, body) {
      return http.request(`/admin/node-groups/${id}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
    },

    refreshPreviewNodeGroup(id) {
      return http.request(`/admin/node-groups/${id}/refresh-preview`, { method: "POST" });
    },

    importNodeGroup(id, body) {
      return http.request(`/admin/node-groups/${id}/import`, {
        method: "POST",
        body: JSON.stringify(body),
      });
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

    async uploadImage(file) {
      const form = new FormData();
      form.append("file", file);
      // 不设 Content-Type：http 客户端识别 FormData 后交给浏览器带 boundary
      const result = await http.request<ImageUploadResponse>("/admin/uploads/images", {
        method: "POST",
        body: form,
      });
      return result.url;
    },

    listEnterprises() {
      return http.request("/admin/enterprises");
    },

    createEnterprise(body) {
      return http.request("/admin/enterprises", { method: "POST", body: JSON.stringify(body) });
    },

    updateEnterprise(id, body) {
      return http.request(`/admin/enterprises/${id}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
    },

    deleteEnterprise(id) {
      return http.request(`/admin/enterprises/${id}`, { method: "DELETE" });
    },

    getLinkHealth(days) {
      return http.request(`/admin/link-health?days=${days ?? 7}`);
    },
  };
}
