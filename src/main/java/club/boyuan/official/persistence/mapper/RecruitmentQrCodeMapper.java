package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.RecruitmentQrCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 招新二维码。查询都是简单条件，直接用 MyBatis-Plus 的条件构造器，不写 XML */
@Mapper
public interface RecruitmentQrCodeMapper extends BaseMapper<RecruitmentQrCode> {
}
