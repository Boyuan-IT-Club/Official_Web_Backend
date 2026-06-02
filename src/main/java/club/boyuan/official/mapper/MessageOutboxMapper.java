package club.boyuan.official.mapper;

import club.boyuan.official.entity.MessageOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {

    @Select("""
            SELECT * FROM message_outbox
            WHERE status = 0 AND retry_count < #{maxRetries}
            ORDER BY id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<MessageOutbox> selectPendingForRelay(@Param("limit") int limit, @Param("maxRetries") int maxRetries);

    @Update("""
            UPDATE message_outbox
            SET status = 1, sent_at = NOW(), last_error = NULL
            WHERE id = #{id} AND status = 0
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE message_outbox
            SET retry_count = retry_count + 1, last_error = #{error}
            WHERE id = #{id}
            """)
    int incrementRetry(@Param("id") Long id, @Param("error") String error);

    @Update("""
            UPDATE message_outbox
            SET status = 2, last_error = #{error}
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id, @Param("error") String error);
}
