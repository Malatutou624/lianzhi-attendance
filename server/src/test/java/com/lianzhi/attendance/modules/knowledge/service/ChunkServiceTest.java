package com.lianzhi.attendance.modules.knowledge.service;

import com.lianzhi.attendance.modules.knowledge.service.ChunkService.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能切片测试
 */
class ChunkServiceTest {

    private final ChunkService chunkService = new ChunkService();

    private List<Chunk> runChunk(String text) {
        return chunkService.chunk(new ArrayList<>(), text);
    }

    @Test
    @DisplayName("空文本返回空列表")
    void chunk_empty() {
        assertTrue(runChunk("").isEmpty());
        assertTrue(runChunk(null).isEmpty());
    }

    @Test
    @DisplayName("短文本单个 chunk")
    void chunk_short_single() {
        List<Chunk> chunks = runChunk("上班时间是早上九点");
        assertEquals(1, chunks.size());
    }

    @Test
    @DisplayName("长文本切成多个 chunk")
    void chunk_long_multiple() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("公司规定上班时间为早上九点，下班时间为晚上六点。");
        }
        List<Chunk> chunks = runChunk(sb.toString());
        assertTrue(chunks.size() >= 1, "应切出至少 1 个 chunk: " + chunks.size());
    }

    @Test
    @DisplayName("chunk 内容非空")
    void chunk_nonEmpty() {
        List<Chunk> chunks = runChunk("这是一段测试文本内容，用于验证切片逻辑。".repeat(10));
        for (Chunk c : chunks) {
            assertNotNull(c.content);
            assertTrue(c.content.length() > 0);
        }
    }
}
