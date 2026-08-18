package com.mintpop.server.service;

import com.mintpop.server.dto.PageResult;
import com.mintpop.server.request.UserSaveRequest;
import com.mintpop.server.response.AdminUserResponse;

public interface AdminUserService {

    /** 分页搜索用户；keyword 为空时返回全部 */
    PageResult<AdminUserResponse> page(String keyword, long pageNo, long pageSize);

    /** 新建用户，返回新用户 id。角色固定为 MEMBER */
    Long create(UserSaveRequest request);

    void update(Long id, UserSaveRequest request);

    void delete(Long id);
}
