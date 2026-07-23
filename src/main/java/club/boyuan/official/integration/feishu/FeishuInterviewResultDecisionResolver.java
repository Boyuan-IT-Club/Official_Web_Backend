package club.boyuan.official.integration.feishu;

/**
 * 根据飞书复选框列解析 interview_result.decision。
 * <p>0 待定, 1 通过, 2 不通过, 3 待调剂
 */
public final class FeishuInterviewResultDecisionResolver {

    private FeishuInterviewResultDecisionResolver() {
    }

    public static int resolve(Boolean interviewPassed, Boolean preselectPassed, Boolean adjustable) {
        if (Boolean.TRUE.equals(adjustable)) {
            return 3;
        }
        if (Boolean.TRUE.equals(interviewPassed) || Boolean.TRUE.equals(preselectPassed)) {
            return 1;
        }
        if (Boolean.FALSE.equals(interviewPassed) || Boolean.FALSE.equals(preselectPassed)) {
            return 2;
        }
        return 0;
    }
}
