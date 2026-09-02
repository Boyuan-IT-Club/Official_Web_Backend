package club.boyuan.official.domain.interview.service;

import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 招新二维码：部门群 / 大群 / 答疑群 */
public interface IRecruitmentQrCodeService {

    /**
     * 上传或替换某个位置的二维码。
     * 同一 (周期, 类型, 部门) 只保留一张 —— 重复上传是覆盖而不是追加。
     */
    RecruitmentQrCode upload(Integer cycleId, String qrType, Integer deptId,
                             String remark, MultipartFile file);

    /** 某周期配好的全部二维码，图片地址已解析成可访问 URL */
    List<RecruitmentQrCode> listByCycle(Integer cycleId);

    /** 删除某一张 */
    void delete(Integer id);

    /**
     * 取某位录取者该看到的二维码：他所在部门那张 + 大群那张。
     * deptId 为空（还没分配部门）时只返回大群。
     */
    List<RecruitmentQrCode> forAdmitted(Integer cycleId, Integer deptId);

    /** 招新答疑群，简历填写页展示；没配则返回 null */
    RecruitmentQrCode qaGroup(Integer cycleId);
}
