package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.SettingKey;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.SystemSettingRepository;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingRepository repository;

    public SystemSettingServiceImpl(SystemSettingRepository repository) {
        this.repository = repository;
    }

    @Override
    public FrontSettings frontSettings() {
        Map<SettingKey, String> stored = repository.findAll();
        return new FrontSettings(
                parseRegion(valueOf(stored, SettingKey.FRONT_REGION)),
                parseInt(valueOf(stored, SettingKey.FRONT_AIRPORTS_PER_USER), SettingKey.FRONT_AIRPORTS_PER_USER),
                parseInt(valueOf(stored, SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS), SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS));
    }

    @Override
    @Transactional
    public FrontSettingsChange updateFrontSettings(FrontSettingsUpdateRequest request) {
        FrontSettings previous = frontSettings();
        FrontSettings current = new FrontSettings(request.getRegion(), request.getAirportsPerUser(), request.getBandwidthPerUserMbps());
        validate(current);
        repository.save(SettingKey.FRONT_REGION, current.region().name());
        repository.save(SettingKey.FRONT_AIRPORTS_PER_USER, Integer.toString(current.airportsPerUser()));
        repository.save(SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS, Integer.toString(current.bandwidthPerUserMbps()));
        return new FrontSettingsChange(previous, current);
    }

    private static void validate(FrontSettings s) {
        if (s.region() == null
                || s.airportsPerUser() < FrontSettings.MIN_AIRPORTS_PER_USER
                || s.airportsPerUser() > FrontSettings.MAX_AIRPORTS_PER_USER
                || s.bandwidthPerUserMbps() < FrontSettings.MIN_BANDWIDTH_PER_USER_MBPS
                || s.bandwidthPerUserMbps() > FrontSettings.MAX_BANDWIDTH_PER_USER_MBPS) {
            throw new BizException(BizCodeEnum.SETTING_INVALID);
        }
    }

    private static String valueOf(Map<SettingKey, String> stored, SettingKey key) {
        return stored.getOrDefault(key, key.getDefaultValue());
    }

    /** 表里的值坏了（手改库、旧版本残留）退回默认值并记日志，不让整站因为一条配置起不来 */
    private static NodeRegion parseRegion(String raw) {
        try {
            return NodeRegion.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("FRONT_REGION 取值 {} 不认识，按默认值 {}", raw, SettingKey.FRONT_REGION.getDefaultValue());
            return NodeRegion.valueOf(SettingKey.FRONT_REGION.getDefaultValue());
        }
    }

    private static int parseInt(String raw, SettingKey key) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            log.warn("{} 取值 {} 不是整数，按默认值 {}", key, raw, key.getDefaultValue());
            return Integer.parseInt(key.getDefaultValue());
        }
    }
}
