package club.boyuan.official.messaging;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class NotificationAction {
    private NotificationAction() {}

    public static String start(String supplied) {
        if (supplied == null) return UUID.randomUUID().toString();
        try {
            String normalized = UUID.fromString(supplied).toString();
            if (normalized.equalsIgnoreCase(supplied)) return normalized;
        } catch (IllegalArgumentException ignored) {}
        throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED, "通知操作号必须是 UUID");
    }

    public static String item(String action, String category, int id) {
        return UUID.nameUUIDFromBytes((action + "/" + category + "/" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
