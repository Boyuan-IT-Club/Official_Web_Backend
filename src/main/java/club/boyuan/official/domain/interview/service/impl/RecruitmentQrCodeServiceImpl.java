package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.mapper.RecruitmentQrCodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
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

    enum Storage { LEGACY, DUAL_WRITE, CYCLE }

    @Value("${official.recruitment.qr-storage:LEGACY}")
    private Storage storage = Storage.LEGACY;

    @PostConstruct
    void reportStorageMode() {
        log.info("Recruitment QR storage mode={}", storage);
    }

    private void refreshProjection(Integer cycleId) {
        if (storage != Storage.LEGACY) {
            qrCodeMapper.refreshCycleCodes(cycleId);
        }
    }

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

    private LambdaQueryWrapper<RecruitmentQrCode> atKey(int cycleId, String qrType, int deptId) {
        return new LambdaQueryWrapper<RecruitmentQrCode>()
                .eq(RecruitmentQrCode::getCycleId, cycleId)
                .eq(RecruitmentQrCode::getQrType, qrType)
                .eq(RecruitmentQrCode::getDeptId, deptId);
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
        RecruitmentQrCode existing = qrCodeMapper.selectOne(atKey(cycleId, qrType, normalizedDept));

        if (existing != null) {
            // 同一个位置重复上传是「替换」而不是「再加一张」
            existing.setImageUrl(objectKey).setRemark(remark);
            qrCodeMapper.updateById(existing);
            refreshProjection(cycleId);
            log.info("二维码已替换 cycleId={}, type={}, deptId={}", cycleId, qrType, normalizedDept);
            return resolve(existing);
        }

        RecruitmentQrCode created = new RecruitmentQrCode()
                .setCycleId(cycleId)
                .setQrType(qrType)
                .setDeptId(normalizedDept)
                .setImageUrl(objectKey)
                .setRemark(remark);
        qrCodeMapper.insert(created);
        refreshProjection(cycleId);
        log.info("二维码已上传 cycleId={}, type={}, deptId={}", cycleId, qrType, normalizedDept);
        return resolve(created);
    }

    @Override
    public List<RecruitmentQrCode> listByCycle(Integer cycleId) {
        if (cycleId == null) {
            return List.of();
        }
        return rawCodes(cycleId).stream().map(this::resolve).toList();
    }

    private List<RecruitmentQrCode> rawCodes(Integer cycleId) {
        if (storage == Storage.CYCLE) {
            return qrCodeMapper.readCycleCodes(cycleId);
        }
        return qrCodeMapper.selectList(new LambdaQueryWrapper<RecruitmentQrCode>()
                .eq(RecruitmentQrCode::getCycleId, cycleId)
                .orderByAsc(RecruitmentQrCode::getQrType)
                .orderByAsc(RecruitmentQrCode::getDeptId));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Integer id) {
        if (id == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        RecruitmentQrCode old = qrCodeMapper.selectById(id);
        if (old != null && qrCodeMapper.lockCycle(old.getCycleId()) != null) {
            qrCodeMapper.deleteById(id);
            refreshProjection(old.getCycleId());
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteByKey(Integer cycleId, String qrType, Integer deptId) {
        int normalizedDept = departmentKey(cycleId, qrType, deptId);
        if (qrCodeMapper.lockCycle(cycleId) != null && qrCodeMapper.delete(atKey(cycleId, qrType, normalizedDept)) > 0) {
            refreshProjection(cycleId);
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
