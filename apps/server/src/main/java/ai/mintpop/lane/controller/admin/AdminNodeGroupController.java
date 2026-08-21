package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.NodeGroupCreateRequest;
import ai.mintpop.lane.request.NodeGroupImportRequest;
import ai.mintpop.lane.request.NodeGroupRenameRequest;
import ai.mintpop.lane.request.SubPreviewRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.NodeGroupResponse;
import ai.mintpop.lane.response.SubPreviewNodeResponse;
import ai.mintpop.lane.service.AdminNodeGroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 节点分组与订阅导入。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/node-groups")
public class AdminNodeGroupController {

    private final AdminNodeGroupService adminNodeGroupService;

    public AdminNodeGroupController(AdminNodeGroupService adminNodeGroupService) {
        this.adminNodeGroupService = adminNodeGroupService;
    }

    @PostMapping("/preview")
    public ApiResponse<List<SubPreviewNodeResponse>> preview(@Valid @RequestBody SubPreviewRequest request) {
        return ApiResponse.success(adminNodeGroupService.preview(request.getSubUrl()));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody NodeGroupCreateRequest request) {
        return ApiResponse.success(adminNodeGroupService.create(request));
    }

    @GetMapping
    public ApiResponse<List<NodeGroupResponse>> list() {
        return ApiResponse.success(adminNodeGroupService.list());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> rename(@PathVariable Long id, @Valid @RequestBody NodeGroupRenameRequest request) {
        adminNodeGroupService.rename(id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/refresh-preview")
    public ApiResponse<List<SubPreviewNodeResponse>> refreshPreview(@PathVariable Long id) {
        return ApiResponse.success(adminNodeGroupService.refreshPreview(id));
    }

    @PostMapping("/{id}/import")
    public ApiResponse<Void> importNodes(@PathVariable Long id, @Valid @RequestBody NodeGroupImportRequest request) {
        adminNodeGroupService.importNodes(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminNodeGroupService.delete(id);
        return ApiResponse.success();
    }
}
