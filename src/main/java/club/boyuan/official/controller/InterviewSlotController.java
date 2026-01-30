package club.boyuan.official.controller;


import club.boyuan.official.dto.CreateInterviewSlotRequestDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.mapper.InterviewSlotMapper;
import club.boyuan.official.service.IInterviewSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 面试时段配置表 前端控制器
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@RestController
@RequestMapping("/api/interview/slot")
@RequiredArgsConstructor
@Slf4j
public class InterviewSlotController {

    private final IInterviewSlotService interviewSlotService;
    @PostMapping("/create")
    public ResponseEntity<ResponseMessage<InterviewSlot>> createInterviewSlot(@Valid @RequestBody CreateInterviewSlotRequestDTO requestDTO) {
        try {
            InterviewSlot createdSlot = interviewSlotService.createInterviewSlot(requestDTO);
            ResponseMessage<InterviewSlot> response = ResponseMessage.success();
            return ResponseEntity.ok(response);
        }catch (Exception e){
            log.error("创建面试时段失败", e);
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "创建面试时段失败"));
        }
    }

}
