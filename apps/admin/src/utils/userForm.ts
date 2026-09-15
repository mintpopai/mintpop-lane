import type {
  AdminNodeResponse,
  AdminUserResponse,
  UserSaveRequest,
  UserStatus,
} from "../api/types";

export interface UserFormModel {
  id: number;
  status: UserStatus;
  frontNodeId: number | null;
  landNodeId: number | null;
  /**
   * 管理员自用说明。刻意设成必填而不是可选：更新接口是整体保存，
   * 处置态那几处快捷操作（停用/恢复/吊销）也走它——字段必填，TypeScript
   * 就会逼着每个调用点把现值带回来，「停用一下备注被清空」在编译期就发生不了。
   */
  remark: string;
}

export function userToForm(user: AdminUserResponse): UserFormModel {
  return {
    id: user.id,
    status: user.status,
    frontNodeId: user.frontNodeId,
    landNodeId: user.landNodeId,
    // 服务端没写备注时回 null，输入框要的是空串
    remark: user.remark ?? "",
  };
}

export function buildUserPayload(form: UserFormModel): UserSaveRequest {
  return {
    status: form.status,
    // 下拉清空时可能产出 undefined 而非 null，这里统一收成 null，
    // 让「未分配」在接口上只有一种表示，类型契约才与实际下发的 JSON 一致
    frontNodeId: form.frontNodeId ?? null,
    landNodeId: form.landNodeId ?? null,
    remark: form.remark.trim(),
  };
}

/** 可分配的第一跳节点：角色对、且启用 */
export function selectableFrontNodes(nodes: AdminNodeResponse[]): AdminNodeResponse[] {
  return nodes.filter((node) => node.role === "FRONT" && node.status === "ENABLED");
}

/**
 * 可分配的落地节点：角色对、启用、且还有剩余容量（已绑人数 < 容量）。
 * 当前用户自己绑着的那个要保留（即使已满），否则编辑时下拉框里会看不到自己已选的值。
 */
export function selectableLandNodes(
  nodes: AdminNodeResponse[],
  currentLandNodeId: number | null,
): AdminNodeResponse[] {
  return nodes.filter(
    (node) =>
      node.role === "LAND" &&
      node.status === "ENABLED" &&
      ((node.assignedUserCount ?? 0) < (node.capacity ?? 0) || node.id === currentLandNodeId),
  );
}
