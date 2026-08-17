package club.boyuan.official.domain.evaluation.dto;

import lombok.Data;

/**
 * 加密报告单信封(context 契约,见工具仓 src/crypto.ts):
 * autograding_report.json 的内容 = 本结构 JSON 序列化。
 */
@Data
public class EncryptedPayload {
    private String algorithm;
    private String iv;
    private String ciphertext;
    private String tag;
}