package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.entity.Airport;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.request.AirportSaveRequest;
import ai.mintpop.lane.response.AirportResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AdminAirportServiceImpl implements AdminAirportService {

    private final AirportRepository airportRepository;
    private final AirportSubscriptionRepository airportSubscriptionRepository;

    public AdminAirportServiceImpl(AirportRepository airportRepository,
                                   AirportSubscriptionRepository airportSubscriptionRepository) {
        this.airportRepository = airportRepository;
        this.airportSubscriptionRepository = airportSubscriptionRepository;
    }

    @Override
    public List<AirportResponse> list() {
        Map<Long, List<AirportSubscriptionDto>> subsByAirport = airportSubscriptionRepository.findAll().stream()
                .collect(Collectors.groupingBy(AirportSubscriptionDto::getAirportId));
        return airportRepository.findAll().stream()
                .map(airport -> toResponse(airport, subsByAirport.getOrDefault(airport.getId(), List.of())))
                .toList();
    }

    @Override
    public Long create(AirportSaveRequest request) {
        if (airportRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.AIRPORT_NAME_DUPLICATED);
        }
        Airport airport = new Airport();
        apply(airport, request);
        return wrapUniqueViolation(() -> airportRepository.create(airport));
    }

    @Override
    public void update(Long id, AirportSaveRequest request) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.AIRPORT_NOT_FOUND));
        if (airportRepository.existsByNameExcludingId(request.getName(), id)) {
            throw new BizException(BizCodeEnum.AIRPORT_NAME_DUPLICATED);
        }
        apply(airport, request);
        wrapUniqueViolation(() -> {
            airportRepository.update(airport);
            return null;
        });
    }

    @Override
    public void delete(Long id) {
        airportRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.AIRPORT_NOT_FOUND));
        // 外键会挡住，但那是原始数据库异常；先查给出可读错误
        if (airportSubscriptionRepository.existsByAirportId(id)) {
            throw new BizException(BizCodeEnum.AIRPORT_IN_USE);
        }
        airportRepository.deleteById(id);
    }

    /** 唯一约束兜底：预检查之后两个管理员同时提交仍可能撞名 */
    private <T> T wrapUniqueViolation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(BizCodeEnum.AIRPORT_NAME_DUPLICATED);
        }
    }

    private void apply(Airport airport, AirportSaveRequest request) {
        airport.setName(request.getName());
        airport.setWebsiteUrl(blankToNull(request.getWebsiteUrl()));
        airport.setRemark(blankToNull(request.getRemark()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AirportResponse toResponse(Airport airport, List<AirportSubscriptionDto> subscriptions) {
        int capacity = subscriptions.stream()
                .mapToInt(s -> FrontAllocationPlanner.primaryCapacity(s.getBandwidthMbps()))
                .sum();
        return new AirportResponse(airport.getId(), airport.getName(), airport.getWebsiteUrl(), airport.getRemark(),
                subscriptions.size(), capacity, airport.getCreatedAt(), airport.getUpdatedAt());
    }
}
