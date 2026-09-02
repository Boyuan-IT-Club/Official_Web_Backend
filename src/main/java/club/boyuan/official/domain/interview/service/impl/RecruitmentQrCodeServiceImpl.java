package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.service.IRecruitmentQrCodeService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import club.boyuan.official.persistence.mapper.RecruitmentQrCodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecruitmentQrCodeServiceImpl implements IRecruitmentQrCodeService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentQrCodeServiceImpl.class);

    private static final Set<String> VALID_TYPES = Set.of(
            RecruitmentQrCode.TYPE_DEPT,
            RecruitmentQrCode.TYPE_MAIN_GROUP,
            RecruitmentQrCode.TYPE_QA_GROUP);

    /** 二维码通常几十 KB，给到 2MB 足够；限制住免得有人传整张截图 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final RecruitmentQrCodeMapper qrCodeMapper;
    private final CosStorageService cosStorageService;

    @Override
    @Transactional
    public RecruitmentQrCode upload(Integer cycleId, String qrType, Integer deptId,
                                    String remark, MultipartFile file) {
        if (cycleId == null || !StringUtils.hasText(qrType)) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        if (!VALID_TYPES.contains(qrType)) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "二维码类型只能是 DEPT / MAIN_GROUP / QA_GROUP，收到: " + qrType);
        }
        if (RecruitmentQrCode.TYPE_DEPT.equals(qrType) && (deptId == null || deptId <= 0)) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "部门群二维码必须指定部门");
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

        // 非部门类型统一用 0 占位：唯一索引允许多个 NULL，
        // 用 NULL 的话大群二维码能被重复插入多条
        int normalizedDept = RecruitmentQrCode.TYPE_DEPT.equals(qrType)
                ? deptId : RecruitmentQrCode.NO_DEPT;

        RecruitmentQrCode existing = qrCodeMapper.selectOne(new LambdaQueryWrapper<RecruitmentQrCode>()
                .eq(RecruitmentQrCode::getCycleId, cycleId)
                .eq(RecruitmentQrCode::getQrType, qrType)
                .eq(RecruitmentQrCode::getDeptId, normalizedDept));

        if (existing != null) {
            // 同一个位置重复上传是「替换」而不是「再加一张」
            existing.setImageUrl(objectKey).setRemark(remark);
            qrCodeMapper.updateById(existing);
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
        log.info("二维码已上传 cycleId={}, type={}, deptId={}", cycleId, qrType, normalizedDept);
        return resolve(created);
    }

    @Override
    public List<RecruitmentQrCode> listByCycle(Integer cycleId) {
        if (cycleId == null) {
            return List.of();
        }
        return qrCodeMapper.selectList(new LambdaQueryWrapper<RecruitmentQrCode>()
                        .eq(RecruitmentQrCode::getCycleId, cycleId)
                        .orderByAsc(RecruitmentQrCode::getQrType)
                        .orderByAsc(RecruitmentQrCode::getDeptId))
                .stream().map(this::resolve).toList();
    }

    @Override
    public void delete(Integer id) {
        if (id == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        qrCodeMapper.deleteById(id);
    }

    @Override
    public List<RecruitmentQrCode> forAdmitted(Integer cycleId, Integer deptId) {
        if (cycleId == null) {
            return List.of();
        }
        List<RecruitmentQrCode> out = new ArrayList<>(2);

        if (deptId != null && deptId > 0) {
            RecruitmentQrCode dept = qrCodeMapper.selectOne(new LambdaQueryWrapper<RecruitmentQrCode>()
                    .eq(RecruitmentQrCode::getCycleId, cycleId)
                    .eq(RecruitmentQrCode::getQrType, RecruitmentQrCode.TYPE_DEPT)
                    .eq(RecruitmentQrCode::getDeptId, deptId));
            if (dept != null) {
                out.add(resolve(dept));
            }
        }

        RecruitmentQrCode main = qrCodeMapper.selectOne(new LambdaQueryWrapper<RecruitmentQrCode>()
                .eq(RecruitmentQrCode::getCycleId, cycleId)
                .eq(RecruitmentQrCode::getQrType, RecruitmentQrCode.TYPE_MAIN_GROUP)
                .eq(RecruitmentQrCode::getDeptId, RecruitmentQrCode.NO_DEPT));
        if (main != null) {
            out.add(resolve(main));
        }
        return out;
    }

    @Override
    public RecruitmentQrCode qaGroup(Integer cycleId) {
        if (cycleId == null) {
            return null;
        }
        RecruitmentQrCode qa = qrCodeMapper.selectOne(new LambdaQueryWrapper<RecruitmentQrCode>()
                .eq(RecruitmentQrCode::getCycleId, cycleId)
                .eq(RecruitmentQrCode::getQrType, RecruitmentQrCode.TYPE_QA_GROUP)
                .eq(RecruitmentQrCode::getDeptId, RecruitmentQrCode.NO_DEPT));
        return qa == null ? null : resolve(qa);
    }

    /** 库里存的是 COS 对象键；对外一律返回可直接访问的地址 */
    private RecruitmentQrCode resolve(RecruitmentQrCode qr) {
        if (qr != null && StringUtils.hasText(qr.getImageUrl())) {
            qr.setImageUrl(cosStorageService.resolveAvatarUrl(qr.getImageUrl()));
        }
        return qr;
    }
}
