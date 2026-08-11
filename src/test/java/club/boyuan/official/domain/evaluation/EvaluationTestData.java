package club.boyuan.official.domain.evaluation;

import club.boyuan.official.domain.evaluation.dto.EncryptedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 测试用加密助手:用与 ReportDecryptor 相同的密钥派生,构造合法信封。
 * 与工具仓 src/crypto.ts 的 encrypt 行为一致(iv 12B、tag 16B、均 base64)。
 */
final class EvaluationTestData {

    private static final SecureRandom RANDOM = new SecureRandom();

    private EvaluationTestData() {
    }

    /** 把明文加密成信封 JSON 字符串。 */
    static String encryptEnvelopeJson(String plain) throws Exception {
        byte[] key = ReportDecryptor.deriveKey();
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ctAndTag = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        byte[] ciphertext = new byte[ctAndTag.length - 16];
        byte[] tag = new byte[16];
        System.arraycopy(ctAndTag, 0, ciphertext, 0, ciphertext.length);
        System.arraycopy(ctAndTag, ciphertext.length, tag, 0, 16);

        EncryptedPayload payload = new EncryptedPayload();
        payload.setAlgorithm("aes-256-gcm");
        payload.setIv(Base64.getEncoder().encodeToString(iv));
        payload.setCiphertext(Base64.getEncoder().encodeToString(ciphertext));
        payload.setTag(Base64.getEncoder().encodeToString(tag));
        return new ObjectMapper().writeValueAsString(payload);
    }

    /** 把明文加密成信封 JSON 后再 base64(模拟 intake 请求体的 report 字段)。 */
    static String encryptEnvelopeB64(String plain) throws Exception {
        return Base64.getEncoder().encodeToString(encryptEnvelopeJson(plain).getBytes(StandardCharsets.UTF_8));
    }

    /** 一份合法报告明文。 */
    static String sampleReportJson() {
        return "{\"author\":\"alice\",\"timestamp\":\"2026-08-10T12:00:00\","
                + "\"tasks\":{\"task1\":{\"score\":100,\"max_score\":100,\"test_results\":[{\"name\":\"env\",\"passed\":true,\"points\":100}]},"
                + "\"task2\":{\"score\":80,\"max_score\":100,\"test_results\":[]},"
                + "\"task3\":{\"score\":90,\"max_score\":100,\"test_results\":[]},"
                + "\"task4\":{\"score\":70,\"max_score\":100,\"test_results\":[]}},"
                + "\"total_score\":340}";
    }
}