package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.response.AdminDeviceRebindRequestResponse;

import java.util.List;

/** 管理员侧的设备管理：裁决换机申请、强制解绑。 */
public interface AdminDeviceService {

    /** 换机申请列表。status 为 null 即全部历史 */
    List<AdminDeviceRebindRequestResponse> listRebindRequests(RebindRequestStatus status);

    /** 同意：申请置 APPROVED 并把订阅改绑到申请的目标设备 */
    void approve(Long requestId, Long adminUserId);

    /** 拒绝：只改申请状态，绑定一动不动 */
    void reject(Long requestId, Long adminUserId);

    /** 强制解绑：清空订阅的绑定，并作废它挂着的待处理申请 */
    void unbind(Long subscriptionId);
}
