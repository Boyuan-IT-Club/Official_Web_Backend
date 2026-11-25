package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.mapper.RecruitmentQrCodeMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecruitmentQrCodeServiceImpl implements IRecruitmentQrCodeService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentQrCodeServiceImpl.class);

    private static final Set<String> VALID_TYPES = Set.of(
            RecruitmentQrCode.TYPE_DEPT,
            RecruitmentQrCode.TYPE_MAIN_GROUP,
            RecruitmentQrCode.TYPE_MAIN_GROUP_2,
            RecruitmentQrCode.TYPE_QA_GROUP);

    /** 二维码通常几十 KB，给到 2MB 足够；限制住免得有人传整张截图 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final RecruitmentQrCodeMapper qrCodeMapper;
    private final CosStorageService cosStorageService;

    private int departmentKey(Integer cycleId, String qrType, Integer deptId) {
        if (cycleId == null || !StringUtils.hasText(qrType)) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        if (!VALID_TYPES.contains(qrType)) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "二维码类型只能是 DEPT / MAIN_GROUP / MAIN_GROUP_2 / QA_GROUP，收到: " + qrType);
        }
        if (RecruitmentQrCode.TYPE_DEPT.equals(qrType) && (deptId == null || deptId <= 0)) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "部门群二维码必须指定部门");
        }
        return RecruitmentQrCode.TYPE_DEPT.equals(qrType) ? deptId : RecruitmentQrCode.NO_DEPT;
    }

    private static String path(String type, int dept) {
        return RecruitmentQrCode.TYPE_DEPT.equals(type) ? "$.DEPT.\"" + dept + "\"" : "$." + type;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecruitmentQrCode upload(Integer cycleId, String qrType, Integer deptId,
                                    String remark, MultipartFile file) {
        int normalizedDept = departmentKey(cycleId, qrType, deptId);
        if (normalizedDept != RecruitmentQrCode.NO_DEPT && !qrCodeMapper.departmentExists(normalizedDept)) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请选择二维码图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "二维码图片不要超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "只能上传图片");
        }

        String objectKey;
        try {
            objectKey = cosStorageService.upload(file, "qrcodes");
        } catch (IOException e) {
            log.error("二维码上传失败 cycleId={}, type={}", cycleId, qrType, e);
            throw new BusinessException(BusinessExceptionEnum.FILE_UPLOAD_FAILED);
        }

        if (qrCodeMapper.lockCycle(cycleId) == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
        qrCodeMapper.putCode(cycleId, path(qrType, normalizedDept), normalizedDept != RecruitmentQrCode.NO_DEPT,
                java.util.UUID.randomUUID().toString(), objectKey, remark);
        return resolve(qrCodeMapper.readCycleCodes(cycleId).stream()
                .filter(code -> qrType.equals(code.getQrType()) && normalizedDept == code.getDeptId())
                .findFirst().orElseThrow());
    }

    @Override
    public List<RecruitmentQrCode> listByCycle(Integer cycleId) {
        if (cycleId == null) {
            return List.of();
        }
        return rawCodes(cycleId).stream().map(this::resolve).toList();
    }

    private List<RecruitmentQrCode> rawCodes(Integer cycleId) {
        return qrCodeMapper.readCycleCodes(cycleId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        RecruitmentQrCode before = qrCodeMapper.findIdentifier(identifier);
        if (before == null || qrCodeMapper.lockCycle(before.getCycleId()) == null) return;
        RecruitmentQrCode current = qrCodeMapper.findIdentifier(identifier);
        if (current == null || !Objects.equals(current.getCycleId(), before.getCycleId())) return;
        qrCodeMapper.removeCode(current.getCycleId(), path(current.getQrType(), current.getDeptId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteByKey(Integer cycleId, String qrType, Integer deptId) {
        int normalizedDept = departmentKey(cycleId, qrType, deptId);
        if (qrCodeMapper.lockCycle(cycleId) != null) {
            qrCodeMapper.removeCode(cycleId, path(qrType, normalizedDept));
        }
    }

    @Override
    public List<RecruitmentQrCode> forAdmitted(Integer cycleId, Integer deptId) {
        if (cycleId == null) {
            return List.of();
        }
        return rawCodes(cycleId).stream().filter(qr -> {
            if (RecruitmentQrCode.TYPE_DEPT.equals(qr.getQrType())) {
                return deptId != null && deptId > 0 && Objects.equals(qr.getDeptId(), deptId);
            }
            return RecruitmentQrCode.isMainGroup(qr.getQrType())
                    && Objects.equals(qr.getDeptId(), RecruitmentQrCode.NO_DEPT);
        }).map(this::resolve).toList();
    }

    @Override
    public RecruitmentQrCode qaGroup(Integer cycleId) {
        if (cycleId == null) {
            return null;
        }
        return rawCodes(cycleId).stream()
                .filter(qr -> RecruitmentQrCode.TYPE_QA_GROUP.equals(qr.getQrType())
                        && Objects.equals(qr.getDeptId(), RecruitmentQrCode.NO_DEPT))
                .findFirst().map(this::resolve).orElse(null);
    }

    /** 库里存的是 COS 对象键；对外一律返回可直接访问的地址 */
    private RecruitmentQrCode resolve(RecruitmentQrCode qr) {
        if (qr != null && StringUtils.hasText(qr.getImageUrl())) {
            qr.setImageUrl(cosStorageService.resolveAvatarUrl(qr.getImageUrl()));
        }
        return qr;
    }
}
