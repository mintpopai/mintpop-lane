package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.AirportSaveRequest;
import ai.mintpop.lane.response.AirportResponse;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.service.AdminAirportService;
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

/** 机场维护。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/airports")
public class AdminAirportController {

    private final AdminAirportService adminAirportService;

    public AdminAirportController(AdminAirportService adminAirportService) {
        this.adminAirportService = adminAirportService;
    }

    @GetMapping
    public ApiResponse<List<AirportResponse>> list() {
        return ApiResponse.success(adminAirportService.list());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody AirportSaveRequest request) {
        return ApiResponse.success(adminAirportService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody AirportSaveRequest request) {
        adminAirportService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminAirportService.delete(id);
        return ApiResponse.success();
    }
}
