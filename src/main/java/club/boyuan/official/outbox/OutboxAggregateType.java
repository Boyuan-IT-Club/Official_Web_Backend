package club.boyuan.official.outbox;

public final class OutboxAggregateType {

    public static final String INTERVIEW_BOOKING = "INTERVIEW_BOOKING";
    public static final String FEISHU_SYNC = "FEISHU_SYNC";
    public static final String INTERVIEW_NOTIFICATION = "INTERVIEW_NOTIFICATION";
    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

    private OutboxAggregateType() {
    }
}
