package club.boyuan.official.persistence.mapper;

import club.boyuan.official.persistence.entity.CollabDoc;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 协同文档快照 Mapper
 *
 * @author dhy
 */
public interface CollabDocMapper extends BaseMapper<CollabDoc> {
    @org.apache.ibatis.annotations.Select("SELECT doc_name,cycle_id,locked,author_state,snapshot_revision FROM collab_doc WHERE doc_name=#{name} FOR UPDATE")
    CollabDoc lockSnapshot(String name);

}
