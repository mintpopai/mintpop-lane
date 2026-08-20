import type { AdminNodeResponse, AdminUserResponse, UserSaveRequest, UserStatus } from "../api/types";

export interface UserFormModel {
  id: number;
  status: UserStatus;
  frontNodeId: number | null;
  landNodeId: number | null;
}

export function userToForm(user: AdminUserResponse): UserFormModel {
  return {
    id: user.id,
    status: user.status,
    frontNodeId: user.frontNodeId,
    landNodeId: user.landNodeId,
  };
}

export function buildUserPayload(form: UserFormModel): UserSaveRequest {
  return {
    status: form.status,
    frontNodeId: form.frontNodeId,
    landNodeId: form.landNodeId,
  };
}

/** 可分配的第一跳节点：角色对、且启用 */
export function selectableFrontNodes(nodes: AdminNodeResponse[]): AdminNodeResponse[] {
  return nodes.filter((node) => node.role === "FRONT" && node.status === "ENABLED");
}

/**
 * 可分配的落地节点：角色对、启用、且没被别人占用。
 * 当前用户自己占着的那个要保留，否则编辑时下拉框里会看不到自己已选的值。
 */
export function selectableLandNodes(
  nodes: AdminNodeResponse[],
  currentLandNodeId: number | null,
): AdminNodeResponse[] {
  return nodes.filter(
    (node) =>
      node.role === "LAND" &&
      node.status === "ENABLED" &&
      (node.assignedUserName === null || node.id === currentLandNodeId),
  );
}
