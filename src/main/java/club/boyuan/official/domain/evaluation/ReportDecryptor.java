package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.evaluation.dto.EncryptedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 报告单解密器(ADR-0002:Java 重实现,契约见工具仓 src/crypto.ts)。
 *
 * <p>信封 = JSON 对象 {algorithm, iv(base64,12B), ciphertext(base64), tag(base64,16B)},
 * 密钥 = SHA-256(base64.decode("WW91cktleUlzMTZCeXRlcw=="))(即 sha256("YourKeyIs16Bytes")),
 * 可被环境变量 AUTOGRADING_SECRET 覆盖。AES-256-GCM,无 AAD。</p>
 */
public final class ReportDecryptor {

    private static final String DEFAULT_SECRET_B64 = "WW91cktleUlzMTZCeXRlcw==";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private ReportDecryptor() {
    }

    /** 从环境变量或默认值派生 32 字节 AES 密钥。 */
    public static byte[] deriveKey() {
        String b64 = System.getenv("AUTOGRADING_SECRET");
        if (b64 == null || b64.isBlank()) {
            b64 = DEFAULT_SECRET_B64;
        }
        byte[] secret = Base64.getDecoder().decode(b64);
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 解密信封 JSON,返回明文 JSON 字符串。
     *
     * @throws BusinessException 格式无效 / 算法不符 / 解密失败(篡改或密钥不符) → 400
     */
    public static String decrypt(String envelopeJson) {
        EncryptedPayload payload;
        try {
            payload = new ObjectMapper().readValue(envelopeJson, EncryptedPayload.class);
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "报告单格式无效: " + e.getMessage());
        }
        if (payload == null || payload.getIv() == null || payload.getCiphertext() == null || payload.getTag() == null) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "报告单缺少 iv/ciphertext/tag 字段");
        }
        if (!"aes-256-gcm".equals(payload.getAlgorithm())) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "不支持的加密算法: " + payload.getAlgorithm());
        }
        try {
            byte[] iv = Base64.getDecoder().decode(payload.getIv());
            if (iv.length != IV_LENGTH) {
                throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "非法 IV 长度");
            }
            byte[] ciphertext = Base64.getDecoder().decode(payload.getCiphertext());
            byte[] tag = Base64.getDecoder().decode(payload.getTag());

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(deriveKey(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

            byte[] plain = cipher.doFinal(combined);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "报告单解密失败(可能被篡改或密钥不符)");
        }
    }
}