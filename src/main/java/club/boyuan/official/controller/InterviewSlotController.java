package club.boyuan.official.controller;


import club.boyuan.official.dto.CreateInterviewSlotRequestDTO;
import club.boyuan.official.dto.GetInterviewSlotListResponseDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UpdateInterviewSlotDTO;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.mapper.InterviewSlotMapper;
import club.boyuan.official.service.IInterviewSlotService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            log.info("创建面试时段");
            InterviewSlot createdSlot = interviewSlotService.createInterviewSlot(requestDTO);
            ResponseMessage<InterviewSlot> response = ResponseMessage.success(createdSlot);
            return ResponseEntity.ok(response);
        }catch (Exception e){
            log.error("创建面试时段失败", e);
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "创建面试时段失败"));
        }
    }

    @PostMapping("/delete/{slotId}")
    public ResponseEntity<ResponseMessage<Void>> deleteInterviewSlot(@PathVariable Integer slotId) {
        try {
            boolean deleted = interviewSlotService.removeById(slotId);
            if (deleted) {
                return ResponseEntity.ok(ResponseMessage.success());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除面试时段失败", e);
            return ResponseEntity.status(500).body(ResponseMessage.error(500, "删除面试时段失败"));
        }
    }

    @PutMapping("/update/{slotId}")
    public ResponseEntity<ResponseMessage<InterviewSlot>> updateInterviewSlot(@PathVariable Integer slotId,
                                                                              @Valid @RequestBody UpdateInterviewSlotDTO requestDTO) {
        try {
            log.info("更新面试时段");
            InterviewSlot updatedSlot = interviewSlotService.updateInterviewSlot(slotId,requestDTO);
            ResponseMessage<InterviewSlot> response = ResponseMessage.success(updatedSlot);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新面试时段失败", e);
            return ResponseEntity.status(500).body(ResponseMessage.error(500, "更新面试时段失败"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ResponseMessage<GetInterviewSlotListResponseDTO>> listInterviewSlots(
            @RequestParam Integer cycleId,
            @RequestParam(required = false) String interviewDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer interviewType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        try {
            log.info("获取面试时段列表");
            GetInterviewSlotListResponseDTO responseDTO = interviewSlotService.listInterviewSlots(
                    cycleId, interviewDate, startTime, location, status, interviewType, page, size);
            ResponseMessage<GetInterviewSlotListResponseDTO> response = ResponseMessage.success(responseDTO);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取面试时段列表失败", e);
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "获取面试时段列表失败"));
        }
    }


}
