import type {
  AdminNodeResponse,
  AdminUserResponse,
  UserSaveRequest,
  UserStatus,
} from "../api/types";

/**
 * 第一跳下拉里那个「自动分配」档位。它不是节点 id，而是一个动作，
 * 与具体节点 id、以及表示「不分配」的 null 并列成三态。
 */
export const FRONT_SELECTION = {
  AUTO_ALLOCATE: "AUTO_ALLOCATE",
} as const;

/** 第一跳下拉的取值：节点 id / null＝不分配 / AUTO_ALLOCATE＝按故障域自动分配一组 */
export type FrontSelection = number | null | (typeof FRONT_SELECTION)[keyof typeof FRONT_SELECTION];

export interface UserFormModel {
  id: number;
  status: UserStatus;
  /**
   * 第一跳主节点：null 就是字面上的「不分配」，具体 id 是手工指定这一个。
   * 与服务端现值相同即「这次没动第一跳」，前置节点组原样不动。
   */
  frontNodeId: number | null;
  /**
   * 是否要服务端按故障域重新分配一组前置节点。只有链路卡上明确选了「自动分配」才为真；
   * userToForm 一律回填 false——改备注、改状态这些保存绝不该顺手重算别人的前置组。
   */
  reallocateFront: boolean;
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
    // 「重新分配」是一次性动作、不是用户身上的状态，回填永远是 false
    reallocateFront: false,
    landNodeId: user.landNodeId,
    // 服务端没写备注时回 null，输入框要的是空串
    remark: user.remark ?? "",
  };
}

/**
 * 把第一跳下拉的三态取值翻译成接口上的两个字段。
 * 服务端的解读见 UserSaveRequest：reallocateFront 为真时忽略 frontNodeId；
 * 否则 frontNodeId 就是字面意思，且与库里现值相同即「这次没有动第一跳」。
 */
export function frontSelectionToPayload(
  selection: FrontSelection,
): Pick<UserFormModel, "frontNodeId" | "reallocateFront"> {
  if (selection === FRONT_SELECTION.AUTO_ALLOCATE) {
    return { frontNodeId: null, reallocateFront: true };
  }
  return { frontNodeId: selection, reallocateFront: false };
}

export function buildUserPayload(form: UserFormModel): UserSaveRequest {
  return {
    status: form.status,
    // 下拉清空时可能产出 undefined 而非 null，这里统一收成 null，
    // 让「未分配」在接口上只有一种表示，类型契约才与实际下发的 JSON 一致
    frontNodeId: form.frontNodeId ?? null,
    reallocateFront: form.reallocateFront,
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
