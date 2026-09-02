package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 招新二维码：部门群 / 大群 / 答疑群。
 *
 * 配置归管理员（与周期配置同权限 cycle:manage）；查看是学生自己的东西，
 * 只要登录即可，但服务层只会返回「他该看到的那几张」。
 */
@RestController
@RequestMapping("/api/recruitment/qrcodes")
@AllArgsConstructor
public class RecruitmentQrCodeController {

    private final IRecruitmentQrCodeService qrCodeService;

    /** 上传或替换一张。同一 (周期,类型,部门) 只保留一张 */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<RecruitmentQrCode>> upload(
            @RequestParam Integer cycleId,
            @RequestParam String qrType,
            @RequestParam(required = false) Integer deptId,
            @RequestParam(required = false) String remark,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ResponseMessage.success(
                qrCodeService.upload(cycleId, qrType, deptId, remark, file)));
    }

    /** 某周期已配置的全部二维码（管理端配置页用） */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<RecruitmentQrCode>>> list(@RequestParam Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(qrCodeService.listByCycle(cycleId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<String>> delete(@PathVariable Integer id) {
        qrCodeService.delete(id);
        return ResponseEntity.ok(ResponseMessage.success("已删除"));
    }

    /**
     * 招新答疑群，简历填写页展示。
     * 公开给已登录用户 —— 还没投递的人也要能看到答疑群。
     */
    @GetMapping("/qa")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<RecruitmentQrCode>> qaGroup(@RequestParam Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(qrCodeService.qaGroup(cycleId)));
    }
}
