import type { AdminNodeResponse, AdminUserResponse, UserSaveRequest, UserStatus } from "../api/types";

export interface UserFormModel {
  id: number | null;
  subject: string;
  name: string;
  status: UserStatus;
  frontNodeId: number | null;
  landNodeId: number | null;
  /** 留空表示沿用原凭据 */
  claudeCredential: string;
}

export function emptyUserForm(): UserFormModel {
  return {
    id: null,
    subject: "",
    name: "",
    status: "ACTIVE",
    frontNodeId: null,
    landNodeId: null,
    claudeCredential: "",
  };
}

export function userToForm(user: AdminUserResponse): UserFormModel {
  return {
    id: user.id,
    subject: user.subject,
    name: user.name,
    status: user.status,
    frontNodeId: user.frontNodeId,
    landNodeId: user.landNodeId,
    // 服务端不回传凭据，回填一律为空；提交时空串即表示不修改
    claudeCredential: "",
  };
}

export function validateUserForm(form: UserFormModel): string[] {
  const errors: string[] = [];
  if (!form.subject.trim()) {
    errors.push("Logto user id 不能为空");
  }
  if (!form.name.trim()) {
    errors.push("姓名不能为空");
  }
  if (form.frontNodeId === null) {
    errors.push("必须选择第一跳节点");
  }
  return errors;
}

export function buildUserPayload(form: UserFormModel): UserSaveRequest {
  return {
    subject: form.subject.trim(),
    name: form.name.trim(),
    status: form.status,
    frontNodeId: form.frontNodeId as number,
    landNodeId: form.landNodeId,
    // 空串 = 沿用原值。服务端对空白串按 null 处理，不会覆盖已有凭据
    claudeCredential: form.claudeCredential.trim(),
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
