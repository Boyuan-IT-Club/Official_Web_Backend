package club.boyuan.official.domain.interview.controller;


import club.boyuan.official.common.dto.ResponseMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview/slot")
public class InterviewSlotController {
    @RequestMapping({"/create", "/delete/{slotId}", "/update/{slotId}", "/list"})
    public ResponseEntity<ResponseMessage<Void>> retired() {
        return ResponseEntity.status(410).body(ResponseMessage.error(410, "旧预约时段已下线，请使用面试场次和时间窗。"));
    }
}
