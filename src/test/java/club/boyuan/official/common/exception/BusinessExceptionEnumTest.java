package club.boyuan.official.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务码必须唯一。
 * <p>
 * 起因：MEMBER_CLAIM_NOT_FOUND 与 GITHUB_ALREADY_BOUND 曾长期共用 2017
 * （前者 2026-09-10 加入时撞上了 2026-08-11 就存在的后者）。两个语义毫不相干
 * 的错误共用一个码，客户端没法靠码区分——想给「GitHub 已被绑定」做特别提示
 * 的话，会连「认领申请不存在」一起命中。
 * <p>
 * 这条断言让下一次重复在 CI 就炸掉，而不是等到前端发现区分不了才察觉。
 */
class BusinessExceptionEnumTest {

    @Test
    @DisplayName("没有两个枚举共用同一个业务码")
    void codesAreUnique() {
        Map<Integer, List<String>> byCode = new HashMap<>();
        for (BusinessExceptionEnum e : BusinessExceptionEnum.values()) {
            byCode.computeIfAbsent(e.getCode(), k -> new ArrayList<>()).add(e.name());
        }

        List<String> duplicates = new ArrayList<>();
        byCode.forEach((code, names) -> {
            if (names.size() > 1) duplicates.add(code + " -> " + names);
        });

        assertTrue(duplicates.isEmpty(),
                "以下业务码被重复使用，客户端无法靠码区分这些错误：\n  " + String.join("\n  ", duplicates));
    }
}
