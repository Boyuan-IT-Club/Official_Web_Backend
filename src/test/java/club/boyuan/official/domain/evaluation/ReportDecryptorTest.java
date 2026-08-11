package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDecryptorTest {

    @Test
    void decryptRoundTrip() throws Exception {
        String plain = EvaluationTestData.sampleReportJson();
        String envelope = EvaluationTestData.encryptEnvelopeJson(plain);
        assertEquals(plain, ReportDecryptor.decrypt(envelope));
    }

    @Test
    void decryptRejectsTamperedCiphertext() throws Exception {
        String plain = EvaluationTestData.sampleReportJson();
        String envelope = EvaluationTestData.encryptEnvelopeJson(plain);
        // 翻转 ciphertext 的最后一个 base64 字符
        String tampered = envelope.replaceFirst("\"ciphertext\":\"[^\"]*\"", "\"ciphertext\":\"AAAA\"");
        BusinessException ex = assertThrows(BusinessException.class, () -> ReportDecryptor.decrypt(tampered));
        assertTrue(ex.getMessage().contains("解密失败"));
    }

    @Test
    void decryptRejectsInvalidJson() {
        BusinessException ex = assertThrows(BusinessException.class, () -> ReportDecryptor.decrypt("not json"));
        assertTrue(ex.getMessage().contains("格式无效"));
    }

    @Test
    void decryptRejectsMissingFields() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReportDecryptor.decrypt("{\"algorithm\":\"aes-256-gcm\"}"));
        assertTrue(ex.getMessage().contains("缺少"));
    }

    @Test
    void decryptRejectsWrongAlgorithm() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReportDecryptor.decrypt("{\"algorithm\":\"aes-128-cbc\",\"iv\":\"AA==\",\"ciphertext\":\"AA==\",\"tag\":\"AA==\"}"));
        assertTrue(ex.getMessage().contains("算法"));
    }

    @Test
    void decryptRejectsBadBase64Iv() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReportDecryptor.decrypt("{\"algorithm\":\"aes-256-gcm\",\"iv\":\"!!!not b64\",\"ciphertext\":\"AA==\",\"tag\":\"AA==\"}"));
        assertTrue(ex.getMessage().contains("解密失败") || ex.getMessage().contains("格式无效"));
    }
}