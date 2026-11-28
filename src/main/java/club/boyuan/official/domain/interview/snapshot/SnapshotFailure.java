package club.boyuan.official.domain.interview.snapshot;

import org.springframework.http.HttpStatus;

final class SnapshotFailure extends RuntimeException {
    final HttpStatus status;

    SnapshotFailure(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    static SnapshotFailure denied() {
        return new SnapshotFailure(HttpStatus.FORBIDDEN, "评价板权限或范围已变化");
    }

    static SnapshotFailure invalid() {
        return new SnapshotFailure(HttpStatus.BAD_REQUEST, "快照格式或署名无效");
    }

    static SnapshotFailure tooLarge() {
        return new SnapshotFailure(HttpStatus.PAYLOAD_TOO_LARGE, "快照超过传输限制");
    }
}
