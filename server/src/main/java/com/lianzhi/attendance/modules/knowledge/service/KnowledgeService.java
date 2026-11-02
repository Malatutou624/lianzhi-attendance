package com.lianzhi.attendance.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lianzhi.attendance.modules.knowledge.entity.*;
import com.lianzhi.attendance.modules.knowledge.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库主服务：文档上传/列表/删除/切片管理
 */
@Slf4j
@Service
public class KnowledgeService {

    @Value("${lianzhi.knowledge-dir}")
    private String knowledgeDir;

    @Autowired KnCategoryMapper categoryMapper;
    @Autowired KnDocumentMapper documentMapper;
    @Autowired KnChunkMapper chunkMapper;
    @Autowired KnTaskMapper taskMapper;
    @Autowired ParseService parseService;
    @Autowired ChunkService chunkService;
    @Autowired VectorService vectorService;

    private final ObjectMapper json = new ObjectMapper();

    // ============== 分类 ==============
    public List<KnCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<KnCategory>()
                .eq(KnCategory::getActive, true)
                .orderByAsc(KnCategory::getSortOrder));
    }

    public KnCategory saveCategory(KnCategory c) {
        if (c.getId() == null) categoryMapper.insert(c);
        else categoryMapper.updateById(c);
        return c;
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    // ============== 文档 ==============
    public KnDocument getDoc(Long id) { return documentMapper.selectById(id); }

    public List<KnDocument> listDocs(Long categoryId, String status, String keyword) {
        LambdaQueryWrapper<KnDocument> w = new LambdaQueryWrapper<>();
        if (categoryId != null) w.eq(KnDocument::getCategoryId, categoryId);
        if (status != null && !status.isBlank()) w.eq(KnDocument::getStatus, status);
        if (keyword != null && !keyword.isBlank()) w.like(KnDocument::getName, keyword);
        w.orderByDesc(KnDocument::getUploadedAt);
        return documentMapper.selectList(w);
    }

    /** 上传文档 + 创建异步入库任务 */
    @Transactional
    public KnDocument upload(MultipartFile file, String name, Long categoryId,
                             String versionNote, LocalDateTime effectiveAt,
                             String tags, String operator) throws IOException {
        // 1. 存文件
        File dir = new File(knowledgeDir);
        if (!dir.exists()) dir.mkdirs();
        String origName = file.getOriginalFilename();
        String fmt = ParseService.detect(origName);
        String safeName = UUID.randomUUID().toString().replace("-", "") + "." + fmt;
        File saved = new File(dir, safeName);
        file.transferTo(saved);

        // 2. 查重（同文件同大小提示）
        Long dup = documentMapper.selectCount(new LambdaQueryWrapper<KnDocument>()
                .eq(KnDocument::getFileName, origName)
                .eq(KnDocument::getFileSize, file.getSize())
                .eq(KnDocument::getActive, true));
        if (dup != null && dup > 0) {
            log.warn("[Knowledge] 检测到重复文档: {}", origName);
        }

        // 3. 建记录
        KnDocument doc = new KnDocument();
        doc.setName(name != null && !name.isBlank() ? name : origName);
        doc.setFileName(origName);
        doc.setFilePath(saved.getAbsolutePath());
        doc.setFileSize(file.getSize());
        doc.setFormat(fmt);
        doc.setCategoryId(categoryId);
        doc.setVersion(1);
        doc.setVersionNote(versionNote);
        doc.setEffectiveAt(effectiveAt);
        doc.setTags(tags);
        doc.setStatus("pending");
        doc.setUploadedBy(operator);
        doc.setActive(true);
        documentMapper.insert(doc);
        // 注意：异步入库任务由 Controller 层创建，避免循环依赖

        return doc;
    }

    /** 手动触发重新入库（失败后重试，仅重置状态；任务由 Controller 创建） */
    public void reprocess(Long docId) {
        KnDocument doc = documentMapper.selectById(docId);
        if (doc == null) return;
        // 清旧切片
        chunkMapper.deleteByDocId(docId);
        doc.setStatus("pending");
        doc.setParseError(null);
        documentMapper.updateById(doc);
        // 注意：异步入库任务由 Controller 层创建
    }

    /** 删除文档（软删 + 清切片 + 清向量） */
    @Transactional
    public void deleteDoc(Long docId) {
        KnDocument doc = documentMapper.selectById(docId);
        if (doc == null) return;
        doc.setActive(false);
        doc.setStatus("deleted");
        documentMapper.updateById(doc);
        chunkMapper.deleteByDocId(docId);
        // 删物理文件
        try { new File(doc.getFilePath()).delete(); } catch (Exception ignore) {}
    }

    // ============== 切片预览/编辑 ==============
    public List<KnChunk> listChunks(Long docId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<KnChunk>()
                .eq(KnChunk::getDocId, docId)
                .orderByAsc(KnChunk::getIndexNo));
    }

    public KnChunk updateChunk(Long chunkId, String content, String title) {
        KnChunk c = chunkMapper.selectById(chunkId);
        if (c == null) return null;
        c.setContent(content);
        if (title != null) c.setTitle(title);
        chunkMapper.updateById(c);
        return c;
    }

    public void deleteChunk(Long chunkId) { chunkMapper.deleteById(chunkId); }

    /** 直接触发同步入库（管理端"立即入库"按钮） */
    @Transactional
    public KnDocument runIngest(Long docId) {
        KnDocument doc = documentMapper.selectById(docId);
        if (doc == null) return null;

        doc.setStatus("parsing");
        documentMapper.updateById(doc);

        // 清旧
        chunkMapper.deleteByDocId(docId);

        // 1. 解析
        ParseService.ParseResult parsed = parseService.parse(new File(doc.getFilePath()), doc.getFormat());
        if (!parsed.ok) {
            doc.setStatus("failed");
            doc.setParseError(parsed.error);
            documentMapper.updateById(doc);
            return doc;
        }

        doc.setStatus("chunking");
        documentMapper.updateById(doc);

        // 2. 切片
        List<ChunkService.Chunk> chunks = chunkService.chunk(parsed.sections, parsed.text);

        doc.setStatus("embedding");
        documentMapper.updateById(doc);

        // 3. 向量化 + 入库
        List<String> texts = chunks.stream().map(c -> c.content).toList();
        List<double[]> vecs = vectorService.embedBatch(texts);
        String alg = vectorService.algName();

        int idx = 1;
        for (ChunkService.Chunk cc : chunks) {
            KnChunk ch = new KnChunk();
            ch.setDocId(docId);
            ch.setIndexNo(idx++);
            ch.setTitle(cc.title);
            ch.setContent(cc.content);
            ch.setSourceRef(cc.sourceRef);
            ch.setAlg(alg);
            // 取索引对应的向量
            if (vecs.size() >= idx - 1) {
                ch.setEmbedding(VectorService.vecToJson(vecs.get(idx - 2)));
            }
            chunkMapper.insert(ch);
        }

        doc.setStatus("ready");
        documentMapper.updateById(doc);

        log.info("[Knowledge] 文档入库完成: id={} chunks={} alg={}", docId, chunks.size(), alg);
        return doc;
    }

    /** 知识库检索（给 AI 问答 Agent 用） */
    public List<RetrievedChunk> retrieve(String query, int topK, double threshold) {
        // 1. 拿所有 ready + active 的文档的切片
        List<KnDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KnDocument>()
                .eq(KnDocument::getStatus, "ready")
                .eq(KnDocument::getActive, true));
        if (docs.isEmpty()) return List.of();

        List<KnChunk> allChunks = new ArrayList<>();
        for (KnDocument d : docs) {
            allChunks.addAll(chunkMapper.selectList(new LambdaQueryWrapper<KnChunk>().eq(KnChunk::getDocId, d.getId())));
        }
        if (allChunks.isEmpty()) return List.of();

        // 2. query 向量化（TF-IDF 需要 corpus 的 IDF，所以把所有切片 content 丢进去）
        List<String> corpus = allChunks.stream().map(KnChunk::getContent).toList();
        double[] qVec = vectorService.embedQuery(query, corpus);
        if (qVec.length == 0) return List.of();

        // 3. 余弦相似度
        List<RetrievedChunk> scored = new ArrayList<>();
        for (KnChunk c : allChunks) {
            double[] cVec = VectorService.jsonToVec(c.getEmbedding());
            double sim;
            if (cVec.length == qVec.length && cVec.length > 0) {
                sim = VectorService.cosineSim(qVec, cVec);
            } else {
                // 不同维度？DashScope 存的 1536，query 走的 TF-IDF → fallback 关键词重叠度
                sim = keywordOverlap(query, c.getContent());
            }
            if (sim >= threshold) {
                RetrievedChunk rc = new RetrievedChunk();
                rc.chunk = c;
                rc.similarity = sim;
                scored.add(rc);
            }
        }

        scored.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        return scored.stream().limit(topK).toList();
    }

    private double keywordOverlap(String query, String text) {
        // 简单中文：共享字符数 / query 长度
        Set<Character> q = new HashSet<>();
        for (char c : query.replaceAll("\\s+", "").toCharArray()) q.add(c);
        if (q.isEmpty()) return 0;
        int hit = 0;
        for (char c : q) if (text.indexOf(c) >= 0) hit++;
        return (double) hit / q.size();
    }

    public static class RetrievedChunk {
        public KnChunk chunk;
        public double similarity;
    }
}
