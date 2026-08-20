package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.PageResult;
import ai.mintpop.lane.request.UserSaveRequest;
import ai.mintpop.lane.response.AdminUserResponse;

public interface AdminUserService {

    /**
     * 分页搜索用户；keyword 为空时不过滤，hasActiveSubscription 为 null 时不按订阅状态过滤，
     * 非 null 时只返回「有/没有」在期订阅的用户。
     */
    PageResult<AdminUserResponse> page(String keyword, Boolean hasActiveSubscription, long pageNo, long pageSize);

    void update(Long id, UserSaveRequest request);

    void delete(Long id);
}
