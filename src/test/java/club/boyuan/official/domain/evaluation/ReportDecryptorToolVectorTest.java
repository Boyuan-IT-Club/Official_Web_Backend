package club.boyuan.official.domain.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 跨仓库交叉验证(ADR-0002):用工具仓 src/crypto.ts 实际产出的密文向量,
 * 验证官网 Java 解密与之不漂移。向量由工具仓真跑生成——
 * plaintext = {"author":"crosscheck","timestamp":"2026-08-11T00:00:00","tasks":{...},"total_score":100}
 * (工具仓默认密钥,envelope 见下方字面量)。
 */
class ReportDecryptorToolVectorTest {

    private static final String TOOL_ENVELOPE = "{\"algorithm\":\"aes-256-gcm\","
            + "\"iv\":\"uQslNWgDXi77h+Mo\","
            + "\"ciphertext\":\"rnv+Kfn+c8nnN7odyfpSbhOw4ScYLUWtJux8lpbpNdNjWJ14Q6v0gPLrZ/9D6aChIwlnd5fketF6QgmLzOO922cwMtyL0ScV+T21nmi/Ee3GAStfHdBCxRcOjOl25WBgNQ/IlyG1bGHFxv/F3asvs4Bq31gqY7XTf2AbP5zs642XDW6S/PsuX0F94Rbvfbq5qvMe5D/UY3y4UprHJ+B1qTtinqG8XdTy8ALsytpoYidnOlba8s0=\","
            + "\"tag\":\"tRMhAXzNrPtZDPaW7k3gVA==\"}";

    private static final String EXPECTED_PLAIN = "{\"author\":\"crosscheck\",\"timestamp\":\"2026-08-11T00:00:00\","
            + "\"tasks\":{\"task1\":{\"score\":100,\"max_score\":100,\"test_results\":[{\"name\":\"env\",\"passed\":true,\"points\":100}]}},"
            + "\"total_score\":100}";

    @Test
    void javaDecryptsToolProducedCiphertext() {
        assertEquals(EXPECTED_PLAIN, ReportDecryptor.decrypt(TOOL_ENVELOPE));
    }
}