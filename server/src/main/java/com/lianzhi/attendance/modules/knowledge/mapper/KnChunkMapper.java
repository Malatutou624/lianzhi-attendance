package com.lianzhi.attendance.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianzhi.attendance.modules.knowledge.entity.KnChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface KnChunkMapper extends BaseMapper<KnChunk> {
    @Delete("DELETE FROM kn_chunk WHERE doc_id = #{docId}")
    void deleteByDocId(Long docId);
}
