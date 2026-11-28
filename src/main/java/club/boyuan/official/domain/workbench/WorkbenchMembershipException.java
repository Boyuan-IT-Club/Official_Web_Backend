package club.boyuan.official.domain.workbench;

import org.springframework.http.HttpStatus;

/** 工作台成员契约的明确失败。HTTP 状态按接口约定，不沿用用户模块里 404 被映射成 409 的枚举。 */
public final class WorkbenchMembershipException extends RuntimeException {
    private final HttpStatus status;

    public WorkbenchMembershipException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
