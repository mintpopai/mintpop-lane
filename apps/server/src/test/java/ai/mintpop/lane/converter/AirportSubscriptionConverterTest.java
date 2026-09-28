package ai.mintpop.lane.converter;

import ai.mintpop.lane.config.CryptoProperties;
import ai.mintpop.lane.crypto.AesGcmCredentialCipher;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.entity.AirportSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 机场订阅 entity ↔ dto 的往返转换。重点盯 trafficAlertedPct：它是「已推到哪一档」的去重状态，
 * null 是有意义的取值（＝未推过 / 已清档），转换层漏掉它或把 null 吞掉，
 * 额度告警的去重与清档就都会失灵。
 */
@DisplayName("机场订阅转换：额度档位含 null 在内都要原样往返")
class AirportSubscriptionConverterTest {

    private final AirportSubscriptionConverter converter = new AirportSubscriptionConverter(new AesGcmCredentialCipher(testCrypto()));

    /** 测试专用固定密钥（32 字节的 Base64），与 application-test.yaml 同一个值，不是任何环境的真实密钥 */
    private static CryptoProperties testCrypto() {
        CryptoProperties properties = new CryptoProperties();
        properties.setKey("bWludHBvcC10ZXN0LWtleS0wMTIzNDU2Nzg5YWJjZCE=");
        return properties;
    }

    private AirportSubscriptionDto dto(Integer alertedPct) {
        AirportSubscriptionDto dto = new AirportSubscriptionDto();
        dto.setId(7L);
        dto.setName("TaiShan Net");
        dto.setSubUrl("https://sub.example.com/c?token=t");
        dto.setTrafficAlertedPct(alertedPct);
        return dto;
    }

    @Test
    @DisplayName("档位有值时往返不丢")
    void keepsAlertedThreshold() {
        AirportSubscription entity = converter.toEntity(dto(95));
        assertThat(entity.getTrafficAlertedPct()).isEqualTo(95);
        assertThat(converter.toDto(entity).getTrafficAlertedPct()).isEqualTo(95);
    }

    @Test
    @DisplayName("档位为 null（未推过 / 已清档）时往返仍是 null，不被转换层吞成默认值")
    void keepsNullAlertedThreshold() {
        AirportSubscription entity = converter.toEntity(dto(null));
        assertThat(entity.getTrafficAlertedPct()).isNull();
        assertThat(converter.toDto(entity).getTrafficAlertedPct()).isNull();
    }
}
