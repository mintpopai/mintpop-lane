package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.SettingKey;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.SystemSettingRepository;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("全局配置：默认值、解析、校验")
class SystemSettingServiceImplTest {

    @Mock private SystemSettingRepository repository;
    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenReturn(Map.of());
        when(repository.findValue(any())).thenReturn(Optional.empty());
        service = new SystemSettingServiceImpl(repository);
    }

    @Test
    @DisplayName("表里没行时按默认值：US、3 家、每人 20 Mbps")
    void defaultsWhenEmpty() {
        FrontSettings settings = service.frontSettings();
        assertThat(settings).isEqualTo(new FrontSettings(NodeRegion.US, 3, 20));
        assertThat(settings.primaryCapacity(300)).isEqualTo(15);
    }

    @Test
    @DisplayName("表里有值按表里的；主用容量按每人带宽向下取整")
    void readsStoredValues() {
        when(repository.findAll()).thenReturn(Map.of(
                SettingKey.FRONT_AIRPORTS_PER_USER, "2",
                SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS, "50"));
        FrontSettings settings = service.frontSettings();
        assertThat(settings.airportsPerUser()).isEqualTo(2);
        assertThat(settings.primaryCapacity(320)).isEqualTo(6);
    }

    @Test
    @DisplayName("更新：三项都写库并返回新值")
    void updateWritesAllThree() {
        FrontSettingsUpdateRequest request = new FrontSettingsUpdateRequest();
        request.setRegion(NodeRegion.US);
        request.setAirportsPerUser(2);
        request.setBandwidthPerUserMbps(20);

        assertThat(service.updateFrontSettings(request)).isEqualTo(new FrontSettings(NodeRegion.US, 2, 20));
        verify(repository).save(SettingKey.FRONT_REGION, "US");
        verify(repository).save(SettingKey.FRONT_AIRPORTS_PER_USER, "2");
        verify(repository).save(SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS, "20");
    }

    @Test
    @DisplayName("机场数 0 或 11、带宽 0 或 1001 报 SETTING_INVALID")
    void rejectsOutOfRange() {
        for (int[] pair : new int[][]{{0, 20}, {11, 20}, {3, 0}, {3, 1001}}) {
            FrontSettingsUpdateRequest request = new FrontSettingsUpdateRequest();
            request.setRegion(NodeRegion.US);
            request.setAirportsPerUser(pair[0]);
            request.setBandwidthPerUserMbps(pair[1]);
            assertThatThrownBy(() -> service.updateFrontSettings(request))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getBizCode())
                    .isEqualTo(BizCodeEnum.SETTING_INVALID);
        }
    }

    @Test
    @DisplayName("库里地区取值不认识（MARS）回退 US")
    void unknownRegionFallsBack() {
        when(repository.findAll()).thenReturn(Map.of(SettingKey.FRONT_REGION, "MARS"));
        assertThat(service.frontSettings().region()).isEqualTo(NodeRegion.US);
    }

    @Test
    @DisplayName("库里机场数不是整数回退 3")
    void nonNumericAirportsFallsBack() {
        when(repository.findAll()).thenReturn(Map.of(SettingKey.FRONT_AIRPORTS_PER_USER, "abc"));
        assertThat(service.frontSettings().airportsPerUser()).isEqualTo(3);
    }

    @Test
    @DisplayName("库里每人带宽为 0 回退 20，主用容量不会除零")
    void zeroBandwidthFallsBack() {
        when(repository.findAll()).thenReturn(Map.of(SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS, "0"));
        FrontSettings settings = service.frontSettings();
        assertThat(settings.bandwidthPerUserMbps()).isEqualTo(20);
        assertThat(settings.primaryCapacity(300)).isEqualTo(15);
    }

    @Test
    @DisplayName("库里机场数越界（11）回退 3")
    void outOfRangeAirportsFallsBack() {
        when(repository.findAll()).thenReturn(Map.of(SettingKey.FRONT_AIRPORTS_PER_USER, "11"));
        assertThat(service.frontSettings().airportsPerUser()).isEqualTo(3);
    }
}
