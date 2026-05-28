package club.boyuan.official.controller;

import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.service.InterviewFeishuImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试安排与飞书多维表格同步。
 */
@RestController
@RequestMapping("/api/interview/feishu")
@RequiredArgsConstructor
@Slf4j
public class InterviewFeishuController {

    private final InterviewFeishuImportService feishuImportService;

    /**
     * 将面试安排按「面试地点」分组导入对应飞书多维表格，按面试时间升序写入行。
     * <p>
     * 表格列名须预先创建并与 {@link club.boyuan.official.feishu.FeishuBitableColumns} 一致
     * （姓名、意向部门、年级、专业、自我介绍、三类问题、面试评价、简历评分、预选、是否调剂、记录人）。
     * 飞书表格 URL 配置在 {@code interview_slot.feishu_table_url}，或在请求体中传入 {@code feishuTableUrl}。
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<ImportFeishuResponseDTO>> importSchedules(
            @Valid @RequestBody ImportFeishuRequestDTO request) {
        log.info("飞书导入面试安排 cycleId={}, slotId={}, forceUpdate={}",
                request.getCycleId(), request.getSlotId(), request.getForceUpdate());
        ImportFeishuResponseDTO result = feishuImportService.importSchedules(request);
        return ResponseEntity.ok(ResponseMessage.success(result));
    }
}
