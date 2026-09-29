package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.SettingKey;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.SystemSettingRepository;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;
import ai.mintpop.lane.service.SystemSettingService.FrontSettingsChange;
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
    @DisplayName("更新：三项都写库，返回旧新两份，changed 反映是否真变了")
    void updateWritesAllThreeAndReportsChange() {
        FrontSettingsUpdateRequest request = new FrontSettingsUpdateRequest();
        request.setRegion(NodeRegion.US);
        request.setAirportsPerUser(2);
        request.setBandwidthPerUserMbps(20);

        FrontSettingsChange change = service.updateFrontSettings(request);

        assertThat(change.previous()).isEqualTo(new FrontSettings(NodeRegion.US, 3, 20));
        assertThat(change.current()).isEqualTo(new FrontSettings(NodeRegion.US, 2, 20));
        assertThat(change.changed()).isTrue();
        verify(repository).save(SettingKey.FRONT_REGION, "US");
        verify(repository).save(SettingKey.FRONT_AIRPORTS_PER_USER, "2");
        verify(repository).save(SettingKey.FRONT_BANDWIDTH_PER_USER_MBPS, "20");
    }

    @Test
    @DisplayName("与现值相同时 changed 为 false")
    void unchangedWhenSameValues() {
        FrontSettingsUpdateRequest request = new FrontSettingsUpdateRequest();
        request.setRegion(NodeRegion.US);
        request.setAirportsPerUser(3);
        request.setBandwidthPerUserMbps(20);
        assertThat(service.updateFrontSettings(request).changed()).isFalse();
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
}
