package com.mintpop.server.dto;

import java.util.List;

/**
 * 分页结果。自定义这个类型是为了不让 MyBatis-Plus 的 IPage 泄漏到 Service 与接口层。
 */
public record PageResult<T>(List<T> records, long total, long pageNo, long pageSize) {
}
