package com.lianzhi.attendance.modules.knowledge.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TF-IDF 本地向量化测试（零 Key 环境可跑）
 */
class VectorServiceTfIdfTest {

    private final VectorService vectorService = new VectorService();

    @Test
    @DisplayName("embedBatch：批量编码应返回等长向量数组")
    void embedBatch_sameCount() {
        List<String> texts = Arrays.asList("请假分为事假病假", "打卡时间早上九点", "加班调休");
        List<double[]> result = vectorService.embedBatch(texts);
        assertEquals(3, result.size());
        for (double[] vec : result) {
            assertNotNull(vec);
            assertTrue(vec.length > 0);
        }
    }

    @Test
    @DisplayName("向量应非零（L1 范数 > 0）")
    void vector_nonZero() {
        List<double[]> vecs = vectorService.embedBatch(Arrays.asList("考勤打卡"));
        double[] v = vecs.get(0);
        double sum = 0;
        for (double d : v) sum += Math.abs(d);
        assertTrue(sum > 0);
    }

    @Test
    @DisplayName("cosineSim：同文本相似度 = 1.0")
    void cosineSim_identical() {
        List<double[]> vecs = vectorService.embedBatch(Arrays.asList("请假", "请假"));
        double sim = VectorService.cosineSim(vecs.get(0), vecs.get(1));
        assertEquals(1.0, sim, 0.001);
    }

    @Test
    @DisplayName("cosineSim：同主题不同句子相似度 > 不同主题句子")
    void cosineSim_relatedHigher() {
        // 同主题（请假）的两个不同句子
        List<String> sameTopic = Arrays.asList(
                "请假可以分为事假病假年假婚假产假",
                "员工请假分为事假病假年假等多种类型"
        );
        // 完全不同主题
        List<String> diffTopic = Arrays.asList(
                "请假可以分为事假病假年假婚假产假",
                "今天北京下雨了要带伞出门小心滑倒"
        );

        List<double[]> sameVecs = vectorService.embedBatch(sameTopic);
        List<double[]> diffVecs = vectorService.embedBatch(diffTopic);

        double simSameTopic = VectorService.cosineSim(sameVecs.get(0), sameVecs.get(1));
        double simDiffTopic = VectorService.cosineSim(diffVecs.get(0), diffVecs.get(1));

        assertTrue(simSameTopic > simDiffTopic,
                "同主题相似度 " + simSameTopic + " 应高于不同主题 " + simDiffTopic);
    }

    @Test
    @DisplayName("cosineSim：结果范围 [-1, 1]")
    void cosineSim_range() {
        List<double[]> vecs = vectorService.embedBatch(Arrays.asList("A", "B"));
        double sim = VectorService.cosineSim(vecs.get(0), vecs.get(1));
        assertTrue(sim >= -1.0 && sim <= 1.0);
    }
}
