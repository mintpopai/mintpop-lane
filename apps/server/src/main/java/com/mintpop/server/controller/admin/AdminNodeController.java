package com.mintpop.server.controller.admin;

import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.request.NodeSaveRequest;
import com.mintpop.server.response.AdminNodeResponse;
import com.mintpop.server.response.ApiResponse;
import com.mintpop.server.service.AdminNodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 节点池管理。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/nodes")
public class AdminNodeController {

    private final AdminNodeService adminNodeService;

    public AdminNodeController(AdminNodeService adminNodeService) {
        this.adminNodeService = adminNodeService;
    }

    @GetMapping
    public ApiResponse<List<AdminNodeResponse>> list(@RequestParam(required = false) NodeRole role) {
        return ApiResponse.success(adminNodeService.list(role));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody NodeSaveRequest request) {
        return ApiResponse.success(adminNodeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody NodeSaveRequest request) {
        adminNodeService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminNodeService.delete(id);
        return ApiResponse.success();
    }
}
