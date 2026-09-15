package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.RebindRequestStatus;

import java.time.Instant;

/**
 * 管理端看到的一条换机申请。用户邮箱、套餐名、分配号由服务端联查后一并下发——
 * 管理员要判断的是「该不该给这个人换机」，为此在界面上再点开几层去凑信息是不该有的摩擦。
 */
public record AdminDeviceRebindRequestResponse(
        Long id,
        String requestNo,
        Long subscriptionId,
        String subscriptionName,
        String assignmentNo,
        Long userId,
        String userEmail,
        /** 申请时绑定的设备；此前从未绑定过则为 null */
        Device fromDevice,
        Device toDevice,
        String reason,
        RebindRequestStatus status,
        Instant createdAt,
        /** 处理时刻；PENDING 时为 null */
        Instant decidedAt
) {

    /** 设备的展示三要素。机器码本身不下发到管理端——管理员用不上，少一处流出就少一处 */
    public record Device(String name, String os, String model) {
    }
}
