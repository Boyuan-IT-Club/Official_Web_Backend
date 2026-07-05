package club.boyuan.official.outbox;

public final class OutboxStatus {

    public static final int PENDING = 0;
    public static final int SENT = 1;
    public static final int FAILED = 2;

    private OutboxStatus() {
    }
}
