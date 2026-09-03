package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.ResumeAttachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 简历附件。
 *
 * 刻意不自定义查询方法：按简历列附件、按 id 取单条，BaseMapper 都够用。
 * 另见 MapperBuiltinNameCollisionTest —— 自定义方法名撞上 BaseMapper 的
 * 集合方法（如 selectByIds）会静默改变语义。
 */
@Mapper
public interface ResumeAttachmentMapper extends BaseMapper<ResumeAttachment> {
}
