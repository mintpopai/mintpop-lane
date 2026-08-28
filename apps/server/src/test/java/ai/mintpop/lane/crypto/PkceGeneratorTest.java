package ai.mintpop.lane.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceGeneratorTest {

    private final PkceGenerator generator = new PkceGenerator();

    @Test
    @DisplayName("verifier 是 32 字节的 base64url 无填充，长度恒为 43")
    void verifierShape() {
        String verifier = generator.newVerifier();
        assertThat(verifier).hasSize(43).doesNotContain("=", "+", "/");
    }

    @Test
    @DisplayName("challenge 是 verifier 的 SHA-256 base64url 值，同一 verifier 恒定")
    void challengeIsDeterministic() {
        // RFC 7636 附录 B 的示例向量
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertThat(generator.challengeOf(verifier))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    @DisplayName("每次生成的 verifier 与 state 都不同")
    void valuesAreRandom() {
        assertThat(generator.newVerifier()).isNotEqualTo(generator.newVerifier());
        assertThat(generator.newState()).isNotEqualTo(generator.newState());
    }
}
