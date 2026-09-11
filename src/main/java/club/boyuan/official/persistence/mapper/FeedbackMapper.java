package club.boyuan.official.persistence.mapper;

import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.persistence.entity.Feedback;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
    List<FeedbackAdminView> selectAdminPage(@Param("category") String category,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    long countAdmin(@Param("category") String category);
}
