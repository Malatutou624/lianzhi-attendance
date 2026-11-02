package com.lianzhi.attendance.modules.face.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 人脸比对服务 - 感知哈希 (pHash) 算法
 *
 * 原理：
 *   每张图片 → 缩小到 32x32 灰度 → DCT 离散余弦变换 → 取 8x8 低频区域
 *   → 与区域均值比大小 → 生成 64 bit 哈希 (16位hex)
 *
 *   两张图的哈希做汉明距离 (不同 bit 的数量) → 越小越相似
 *   ≤ 12 bit 判定为"同一人" (阈值调得宽松，课程项目容错)
 *
 *   零依赖：只用 Java 标准库 javax.imageio + AWT
 *
 * 课程项目演示用途，足够防止"随便拿别人照片打卡"。
 */
@Service
public class FaceMatchService {

    private static final Logger log = LoggerFactory.getLogger(FaceMatchService.class);

    /** 相似阈值：汉明距离 ≤ 此值判定为同一人 */
    public static final int SAME_PERSON_THRESHOLD = 12;

    @Value("${lianzhi.upload-dir}")
    private String uploadDir;

    /** 虹软 ArcFace SDK（优先用，不可用时 fallback pHash） */
    @Autowired(required = false)
    private ArcSoftFaceService arcFaceService;

    /**
     * 计算图片的感知哈希 (pHash)
     *
     * @param url 图片 URL 或 本地文件相对路径 (如 /uploads/xxx.jpg)
     * @return 16 位 hex 字符串 (64 bit)，失败返回 null
     */
    public String computeHash(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            BufferedImage img = loadImage(url);
            if (img == null) {
                log.warn("[FaceMatch] 加载图片失败: {}", url);
                return null;
            }
            return pHash(img);
        } catch (Exception e) {
            log.error("[FaceMatch] 计算 pHash 异常: {}", url, e);
            return null;
        }
    }

    /**
     * 比对两张图片是否为同一人
     *
     * 策略：虹软 ArcFace SDK（真·人脸识别）优先，不可用时 fallback pHash
     *
     * @param urlA 采集时的人脸照 URL
     * @param urlB 打卡时的现场照 URL
     * @return MatchResult (match/similarity/hammingDistance/templateHash/currentHash/reason)
     */
    public MatchResult compare(String urlA, String urlB) {
        MatchResult result = new MatchResult();

        // ========== 优先：虹软 ArcFace SDK ==========
        if (arcFaceService != null && arcFaceService.isReady()) {
            log.info("[FaceMatch] ➤ 使用虹软 ArcFace SDK 真·人脸识别比对");

            // 1. 检测人脸数量（必须单人照）
            int facesA = arcFaceService.detectFace(urlA);
            int facesB = arcFaceService.detectFace(urlB);
            log.info("[FaceMatch] 虹软检测人脸: 模板照={}张, 现场照={}张", facesA, facesB);

            if (facesA < 1) { result.reason = "模板照片（采集照）未检测到人脸，请重新采集"; return result; }
            if (facesB < 1) { result.reason = "现场照片未检测到人脸，请正对镜头重新拍照"; return result; }
            if (facesA > 1 || facesB > 1) { result.reason = "照片中检测到多张人脸，请单人正对镜头"; return result; }

            // 2. 特征比对
            ArcSoftFaceService.MatchResult arcResult = arcFaceService.compare(urlA, urlB);
            result.similarity = arcResult.similarity;
            result.match = arcResult.match;
            result.reason = arcResult.reason;

            // 3. 活体检测（打卡现场照防照片攻击）
            int liveness = arcFaceService.livenessCheck(urlB);
            log.info("[FaceMatch] 活体检测: liveness={} (1=活体, 0=照片, -1=未知)", liveness);
            if (liveness == 0) {
                result.match = false;
                result.reason = "活体检测未通过：疑似照片攻击，请本人正对镜头";
            }

            result.algorithm = "ArcFace-3.0";
            log.info("[FaceMatch] ✅ 虹软比对完成: similarity={:.1f}%, match={}, liveness={}",
                    result.similarity * 100, result.match, liveness);
            return result;
        }

        // ========== Fallback：本地 pHash ==========
        log.warn("[FaceMatch] ⚠️ 虹软 SDK 不可用，fallback 到 pHash（仅感知哈希，不是真·人脸识别）");
        result.templateHash = computeHash(urlA);
        result.currentHash  = computeHash(urlB);

        log.info("[FaceMatch][pHash] templateHash={}(len={}), currentHash={}(len={})",
                result.templateHash, result.templateHash != null ? result.templateHash.length() : 0,
                result.currentHash, result.currentHash != null ? result.currentHash.length() : 0);

        if (result.templateHash == null) {
            result.reason = "模板照片加载失败（员工人脸照不存在）";
            return result;
        }
        if (result.currentHash == null) {
            result.reason = "现场照片加载失败（请重新拍照）";
            return result;
        }

        result.hammingDistance = safeHammingDistance(result.templateHash, result.currentHash);
        result.match = result.hammingDistance <= SAME_PERSON_THRESHOLD;
        result.similarity = 1.0 - result.hammingDistance / 64.0;
        result.algorithm = "pHash-Fallback";

        log.info("[FaceMatch][pHash] distance={}, similarity={:.1f}%, match={}",
                result.hammingDistance, result.similarity * 100, result.match);
        return result;
    }

    // =====================================================================
    // 下面是纯算法部分：图片加载 → 缩放 → 灰度 → DCT → 哈希 → 汉明距离
    // =====================================================================

    /** 加载图片：先尝试本地 uploads 目录，失败再尝试 HTTP URL */
    private BufferedImage loadImage(String url) throws IOException {
        // 1. 本地路径（/uploads/xxx.jpg → uploads/xxx.jpg）
        if (url.startsWith("/uploads/")) {
            String local = url.replaceFirst("^/", "");
            Path p = Paths.get(uploadDir).resolve(new File(local).getName());
            if (Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    return ImageIO.read(in);
                }
            }
        }
        // 2. HTTP URL
        if (url.startsWith("http")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            }
        }
        // 3. 直接本地相对路径
        File f = new File(url);
        if (f.exists()) return ImageIO.read(f);

        return null;
    }

    /** 感知哈希核心算法 */
    private String pHash(BufferedImage img) {
        // 1. 缩放到 32x32
        BufferedImage scaled = new BufferedImage(32, 32, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(img.getScaledInstance(32, 32, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        // 2. 提取像素矩阵 32x32
        double[][] pixels = new double[32][32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int rgb = scaled.getRGB(x, y) & 0xFF; // 灰度值 0-255
                pixels[y][x] = rgb;
            }
        }

        // 3. DCT 离散余弦变换 (简化版：只做 2D 分离式 DCT)
        double[][] dct = dct2(pixels);

        // 4. 取左上角 8x8 低频区域 + 计算均值
        double sum = 0;
        double[][] lowFreq = new double[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                lowFreq[y][x] = dct[y][x];
                sum += dct[y][x];
            }
        }
        double avg = sum / 64.0;

        // 5. 生成 64 bit 哈希：每个低频值与均值比大小
        long hash = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash <<= 1;
                if (lowFreq[y][x] > avg) hash |= 1L;
            }
        }

        return String.format("%016x", hash); // 16 位 hex = 64 bit
    }

    /** 安全汉明距离：两个 hex 哈希不同 bit 的数量（防御性实现，不会溢出） */
    public int safeHammingDistance(String hashA, String hashB) {
        try {
            // 取较长那个的长度
            int len = Math.max(hashA.length(), hashB.length());
            // 左边补 0 对齐
            String a = padLeft(hashA.toLowerCase(), len, '0');
            String b = padLeft(hashB.toLowerCase(), len, '0');

            int diff = 0;
            for (int i = 0; i < len; i++) {
                int va = hexVal(a.charAt(i));
                int vb = hexVal(b.charAt(i));
                int xor = va ^ vb;
                diff += Integer.bitCount(xor); // 每个 hex 字符 4 bit
            }
            return diff;
        } catch (Exception e) {
            log.warn("[FaceMatch] safeHammingDistance 异常: {}", e.getMessage());
            return 64; // 最坏情况：完全不匹配
        }
    }

    private static String padLeft(String s, int len, char pad) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(len);
        for (int i = s.length(); i < len; i++) sb.append(pad);
        sb.append(s);
        return sb.toString();
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
        if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
        return 0;
    }

    /** @deprecated 用 safeHammingDistance 代替，避免 Long 溢出 */
    @Deprecated
    public int hammingDistance(String hashA, String hashB) {
        return safeHammingDistance(hashA, hashB);
    }

    /** 2D DCT 分离式实现（标准算法，32x32 矩阵） */
    private double[][] dct2(double[][] input) {
        int n = input.length;

        // Step 1: 对每行做 1D DCT
        double[][] rowDct = new double[n][n];
        for (int y = 0; y < n; y++) {
            for (int u = 0; u < n; u++) {
                double sum = 0;
                for (int x = 0; x < n; x++) {
                    sum += input[y][x] * Math.cos((2 * x + 1) * u * Math.PI / (2.0 * n));
                }
                double c = (u == 0) ? Math.sqrt(1.0 / n) : Math.sqrt(2.0 / n);
                rowDct[y][u] = c * sum;
            }
        }

        // Step 2: 对每列做 1D DCT
        double[][] result = new double[n][n];
        for (int x = 0; x < n; x++) {
            for (int v = 0; v < n; v++) {
                double sum = 0;
                for (int y = 0; y < n; y++) {
                    sum += rowDct[y][x] * Math.cos((2 * y + 1) * v * Math.PI / (2.0 * n));
                }
                double c = (v == 0) ? Math.sqrt(1.0 / n) : Math.sqrt(2.0 / n);
                result[v][x] = c * sum;
            }
        }

        return result;
    }

    /** 比对结果 POJO */
    public static class MatchResult {
        public boolean match;
        public int hammingDistance;
        public double similarity;       // 0~1（虹软相似度/64 或 1-汉明距离/64）
        public String templateHash;     // 采集时的人脸哈希（pHash 时有）
        public String currentHash;      // 打卡时的人脸哈希（pHash 时有）
        public String reason;           // 失败原因（空字符串表示成功）
        public String algorithm;        // 使用的算法：ArcFace-3.0 或 pHash-Fallback
    }
}
