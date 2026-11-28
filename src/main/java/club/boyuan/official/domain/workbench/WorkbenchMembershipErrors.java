package club.boyuan.official.domain.workbench;

import club.boyuan.official.common.dto.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = {WorkbenchAccessController.class, WorkbenchDirectoryController.class})
class WorkbenchMembershipErrors {
    private static final Logger log = LoggerFactory.getLogger(WorkbenchMembershipErrors.class);

    @ExceptionHandler(WorkbenchMembershipException.class)
    ResponseEntity<ResponseMessage<Void>> fail(WorkbenchMembershipException error) {
        return ResponseEntity.status(error.status()).body(ResponseMessage.error(error.status().value(), error.getMessage()));
    }

    @ExceptionHandler({CannotCreateTransactionException.class, TransientDataAccessException.class, DataAccessResourceFailureException.class})
    ResponseEntity<ResponseMessage<Void>> unavailable(RuntimeException error) {
        log.warn("成员数据源暂不可用", error);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ResponseMessage.error(503, "成员状态暂时无法确认"));
    }
}
