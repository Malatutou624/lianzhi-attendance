package com.lianzhi.attendance.modules.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Embedding 向量化服务
 *
 * 双引擎：
 *   - tfidf（默认，零 Key 纯本地，课程项目够用）
 *       对文本分词（简单按字+2字组合）→ 算 TF-IDF → 归一化 → 余弦相似度检索
 *   - dashscope（可选，配 api-key 自动启用真 AI）
 *       调用阿里 DashScope text-embedding-v2 → 1536 维 float 向量
 *
 * 向量存储：MySQL kn_chunk.embedding 存 JSON 数组字符串
 * 向量检索：应用层算余弦相似度 top-K（课程项目数据量小，够用）
 */
@Slf4j
@Service
public class VectorService {

    @Value("${lianzhi.knowledge.embedding:tfidf}")
    private String engine;

    @Value("${lianzhi.knowledge.dashscope.api-key:}")
    private String dsKey;
    @Value("${lianzhi.knowledge.dashscope.model:text-embedding-v2}")
    private String dsModel;

    @Value("${lianzhi.knowledge.siliconflow.api-key:}")
    private String sfKey;
    @Value("${lianzhi.knowledge.siliconflow.embedding-model:BAAI/bge-m3}")
    private String sfModel;
    @Value("${lianzhi.knowledge.siliconflow.endpoint-embedding:https://api.siliconflow.cn/v1/embeddings}")
    private String sfEmbeddingEndpoint;

    private final ObjectMapper json = new ObjectMapper();

    /** 当前 engine 是否是真 AI（有 key） */
    private boolean aiEmbeddingAvailable() {
        if ("siliconflow".equalsIgnoreCase(engine)) return sfKey != null && !sfKey.isBlank();
        if ("dashscope".equalsIgnoreCase(engine)) return dsKey != null && !dsKey.isBlank();
        return false;
    }
    private boolean isSiliconFlow() { return "siliconflow".equalsIgnoreCase(engine); }

    /** 向量维度（TF-IDF 动态，AI embedding 固定） */
    public int vectorDim() {
        if (!aiEmbeddingAvailable()) return -1;
        // BAAI/bge-m3 = 1024维, text-embedding-v2 = 1536维
        if (isSiliconFlow()) return 1024;
        return 1536;
    }

    public String algName() {
        if (!aiEmbeddingAvailable()) return "tfidf";
        return isSiliconFlow() ? "siliconflow" : "dashscope";
    }

    /** 批量向量化 */
    public List<double[]> embedBatch(List<String> texts) {
        if (aiEmbeddingAvailable()) {
            if (isSiliconFlow()) return embedSiliconFlow(texts);
            return embedDashScope(texts);
        }
        return embedTfidf(texts);
    }

    public double[] embedQuery(String query, List<String> corpusForIdf) {
        if (aiEmbeddingAvailable()) {
            List<double[]> r;
            if (isSiliconFlow()) r = embedSiliconFlow(List.of(query));
            else r = embedDashScope(List.of(query));
            return r.isEmpty() ? new double[0] : r.get(0);
        }
        return embedTfidfQuery(query, corpusForIdf);
    }

    // ---- DashScope 真 AI（原生格式） ----
    private List<double[]> embedDashScope(List<String> texts) {
        List<double[]> result = new ArrayList<>();
        try {
            URL url = new URL("https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Bearer " + dsKey);
            conn.setRequestProperty("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("model", dsModel);
            Map<String, Object> inputMap = new HashMap<>();
            inputMap.put("texts", texts);
            body.put("input", inputMap);

            byte[] payload = json.writeValueAsBytes(body);
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); }

            int code = conn.getResponseCode();
            if (code == 200) {
                String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> map = json.readValue(resp, new TypeReference<>() {});
                List<Map<String, Object>> embeds = (List) ((Map) map.get("output")).get("embeddings");
                for (Map<String, Object> e : embeds) {
                    List<Double> vec = (List<Double>) e.get("embedding");
                    result.add(vec.stream().mapToDouble(Double::doubleValue).toArray());
                }
            } else {
                log.warn("[VectorService] DashScope 返回 {}，fallback TF-IDF", code);
                result = embedTfidf(texts);
            }
        } catch (Exception ex) {
            log.warn("[VectorService] DashScope 调用失败，fallback TF-IDF: {}", ex.getMessage());
            result = embedTfidf(texts);
        }
        return result;
    }

    // ---- SiliconFlow 真 AI（OpenAI 兼容格式，推荐：免费不限流） ----
    private List<double[]> embedSiliconFlow(List<String> texts) {
        List<double[]> result = new ArrayList<>();
        try {
            URL url = new URL(sfEmbeddingEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Bearer " + sfKey);
            conn.setRequestProperty("Content-Type", "application/json");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", sfModel);
            body.put("input", texts);

            byte[] payload = json.writeValueAsBytes(body);
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); }

            int code = conn.getResponseCode();
            if (code == 200) {
                String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> map = json.readValue(resp, new TypeReference<>() {});
                // OpenAI 兼容格式: { "data": [ { "embedding": [0.1, ...], "index": 0 }, ... ] }
                List<Map<String, Object>> data = (List) map.get("data");
                if (data != null) {
                    // 按 index 排序保证顺序正确
                    data.sort((a, b) -> ((Number) a.get("index")).intValue() - ((Number) b.get("index")).intValue());
                    for (Map<String, Object> e : data) {
                        List<Double> vec = (List<Double>) e.get("embedding");
                        result.add(vec.stream().mapToDouble(Double::doubleValue).toArray());
                    }
                }
                log.info("[VectorService] ✅ SiliconFlow embedding 成功，{} 条，维度 {}", result.size(), result.isEmpty() ? 0 : result.get(0).length);
            } else {
                String err = "";
                try { err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8); } catch (Exception ignore) {}
                log.warn("[VectorService] SiliconFlow 返回 {}: {}，fallback TF-IDF", code, err);
                result = embedTfidf(texts);
            }
        } catch (Exception ex) {
            log.warn("[VectorService] SiliconFlow 调用失败，fallback TF-IDF: {}", ex.getMessage());
            result = embedTfidf(texts);
        }
        return result;
    }

    // ---- TF-IDF 本地 ----
    private List<double[]> embedTfidf(List<String> texts) {
        // 1. 分词
        List<List<String>> tokensList = texts.stream().map(this::tokenize).collect(Collectors.toList());

        // 2. 构建词表
        Set<String> vocab = new LinkedHashSet<>();
        for (List<String> toks : tokensList) vocab.addAll(toks);
        List<String> vocabList = new ArrayList<>(vocab);
        Map<String, Integer> wordIdx = new HashMap<>();
        for (int i = 0; i < vocabList.size(); i++) wordIdx.put(vocabList.get(i), i);

        // 3. 算 IDF
        double[] idf = new double[vocabList.size()];
        int N = texts.size();
        for (int i = 0; i < vocabList.size(); i++) {
            String w = vocabList.get(i);
            int df = 0;
            for (List<String> toks : tokensList) if (toks.contains(w)) df++;
            idf[i] = Math.log((double) N / (df + 1));
        }

        // 4. 算 TF-IDF + L2 归一化
        List<double[]> result = new ArrayList<>();
        for (List<String> toks : tokensList) {
            double[] vec = new double[vocabList.size()];
            for (String t : toks) {
                Integer idx = wordIdx.get(t);
                if (idx != null) vec[idx]++;
            }
            // TF × IDF
            double norm = 0;
            for (int i = 0; i < vec.length; i++) {
                vec[i] *= idf[i];
                norm += vec[i] * vec[i];
            }
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < vec.length; i++) vec[i] /= norm;
            result.add(vec);
        }
        return result;
    }

    private double[] embedTfidfQuery(String query, List<String> corpus) {
        List<String> all = new ArrayList<>(corpus);
        all.add(query);
        List<double[]> allVec = embedTfidf(all);
        return allVec.get(allVec.size() - 1);
    }

    /** 简单中文分词：单字 + 相邻2字组合（Bi-gram），够课程项目用 */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) return tokens;
        String clean = text.toLowerCase().replaceAll("\\s+", "");
        // 去掉纯标点
        StringBuilder sb = new StringBuilder();
        for (char c : clean.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c >= '\u4e00' && c <= '\u9fff') sb.append(c);
        }
        String t = sb.toString();
        // 单字
        for (char c : t.toCharArray()) tokens.add(String.valueOf(c));
        // 2字组合
        for (int i = 0; i < t.length() - 1; i++) tokens.add(t.substring(i, i + 2));
        return tokens;
    }

    // ---- 工具 ----
    public static double cosineSim(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static String vecToJson(double[] vec) {
        try {
            return new ObjectMapper().writeValueAsString(vec);
        } catch (Exception e) {
            return "[]";
        }
    }

    public static double[] jsonToVec(String json) {
        if (json == null || json.isBlank()) return new double[0];
        try {
            List<Double> list = new ObjectMapper().readValue(json, new TypeReference<>() {});
            return list.stream().mapToDouble(Double::doubleValue).toArray();
        } catch (Exception e) {
            return new double[0];
        }
    }
}
