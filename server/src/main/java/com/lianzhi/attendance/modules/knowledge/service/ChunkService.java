package com.lianzhi.attendance.modules.knowledge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能语义切片
 *
 * 策略：
 *   1. 优先按段落切（\n\n 或明显标题行）
 *   2. 过长段落再按句号/逗号切
 *   3. 单条 ≤ maxChars，相邻重叠 overlapChars
 *   4. 过滤乱码/重复/全空切片
 */
@Service
public class ChunkService {

    @Value("${lianzhi.knowledge.chunk-max-chars:500}")
    private int maxChars;

    @Value("${lianzhi.knowledge.chunk-overlap:80}")
    private int overlap;

    public static class Chunk {
        public int indexNo;
        public String title;
        public String content;
        public String sourceRef;

        public Chunk(int i, String title, String content, String sourceRef) {
            this.indexNo = i;
            this.title = title;
            this.content = content;
            this.sourceRef = sourceRef;
        }
    }

    /**
     * @param sections ParseService 返回的分段（每页/每段一个）
     * @param fullText 全文（备用）
     */
    public List<Chunk> chunk(List<String> sections, String fullText) {
        List<Chunk> result = new ArrayList<>();
        int idx = 1;

        if (sections != null && !sections.isEmpty()) {
            for (String sec : sections) {
                List<Chunk> sub = chunkOneSection(sec, idx, "");
                result.addAll(sub);
                idx += sub.size();
            }
        } else if (fullText != null && !fullText.isBlank()) {
            List<Chunk> sub = chunkOneSection(fullText, idx, "");
            result.addAll(sub);
        }

        // 去乱码/过短
        List<Chunk> clean = new ArrayList<>();
        for (Chunk c : result) {
            String text = c.content == null ? "" : c.content.trim();
            if (text.length() >= 10 && !isGarbled(text)) {
                c.content = text;
                clean.add(c);
            }
        }

        // 重新编号
        for (int i = 0; i < clean.size(); i++) clean.get(i).indexNo = i + 1;

        return clean;
    }

    private List<Chunk> chunkOneSection(String section, int startIdx, String sourceRef) {
        List<Chunk> out = new ArrayList<>();
        String s = section == null ? "" : section.trim();
        if (s.isEmpty()) return out;

        // 尝试按自然段落切
        String[] paras = s.split("\\n\\s*\\n+");
        List<String> units = new ArrayList<>();
        for (String p : paras) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (t.length() <= maxChars) {
                units.add(t);
            } else {
                // 过长段落 → 按句号/感叹号/问号/分号 再切
                for (String sub : splitBySentence(t, maxChars)) units.add(sub);
            }
        }

        // 加重叠窗口，防止语义截断
        String prevTail = "";
        for (String unit : units) {
            String withOverlap = (prevTail.isEmpty() ? "" : prevTail) + unit;
            // 如果加了重叠就超长，截一下
            if (withOverlap.length() > maxChars + overlap) {
                withOverlap = withOverlap.substring(Math.max(0, withOverlap.length() - maxChars - overlap));
            }
            String title = extractTitle(unit);
            out.add(new Chunk(out.size() + startIdx, title, withOverlap.trim(), sourceRef));

            // 取本单元末尾 overlap 字符作为下条重叠
            if (unit.length() > overlap) prevTail = unit.substring(unit.length() - overlap);
            else prevTail = unit;
        }

        // 无段落的 fallback：直接按字符数滑窗
        if (out.isEmpty()) {
            for (int i = 0; i < s.length(); i += maxChars - overlap) {
                int end = Math.min(i + maxChars, s.length());
                out.add(new Chunk(out.size() + startIdx, null, s.substring(i, end).trim(), sourceRef));
                if (end == s.length()) break;
            }
        }

        return out;
    }

    /** 按中英文句号切，单段 ≤ limit */
    private List<String> splitBySentence(String text, int limit) {
        List<String> out = new ArrayList<>();
        // 按句末标点切
        String[] raw = text.split("(?<=[。！？!?；;])");
        StringBuilder buf = new StringBuilder();
        for (String s : raw) {
            s = s.trim();
            if (s.isEmpty()) continue;
            if (buf.length() + s.length() <= limit) {
                buf.append(s);
            } else {
                if (buf.length() > 0) out.add(buf.toString().trim());
                buf = new StringBuilder(s);
                // 如果单句本身就超长，再硬切
                while (buf.length() > limit) {
                    out.add(buf.substring(0, limit).trim());
                    buf.delete(0, limit);
                }
            }
        }
        if (buf.length() > 0) out.add(buf.toString().trim());
        return out;
    }

    private String extractTitle(String text) {
        if (text == null) return null;
        // 尝试取第一行（可能是标题）
        int nl = text.indexOf('\n');
        String firstLine = nl > 0 ? text.substring(0, nl).trim() : text.trim();
        if (firstLine.length() > 0 && firstLine.length() <= 60) {
            // 如果像标题（短句、不以标点结尾）
            if (!firstLine.matches(".*[。！？!?]$") && !firstLine.matches(".*[，,]$")) {
                return firstLine;
            }
        }
        return null;
    }

    /** 判断是否乱码（中文占比太低） */
    private boolean isGarbled(String text) {
        int chinese = 0, total = 0;
        for (char c : text.toCharArray()) {
            total++;
            if (c >= '\u4e00' && c <= '\u9fff') chinese++;
        }
        return total > 0 && (double) chinese / total < 0.1;
    }
}
