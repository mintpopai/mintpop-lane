package ai.mintpop.lane.service;

import ai.mintpop.lane.request.AirportSaveRequest;
import ai.mintpop.lane.response.AirportResponse;

import java.util.List;

/** 机场维护：管理端的增删改查 */
public interface AdminAirportService {

    List<AirportResponse> list();

    Long create(AirportSaveRequest request);

    void update(Long id, AirportSaveRequest request);

    /** 机场下还有订阅时拒绝 */
    void delete(Long id);
}
