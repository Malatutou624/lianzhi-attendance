package com.lianzhi.attendance.modules.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 多格式文档解析服务
 *
 * 支持：pdf / docx / xlsx / pptx / txt / csv
 * 输出：纯文本（标题/正文/表格都保留基础结构）
 *
 * 暂不实现：图片OCR、音视频ASR（太重，后续可接百度/阿里API）
 */
@Slf4j
@Service
public class ParseService {

    public static class ParseResult {
        /** 全部解析出来的文本，按分页/段落拼接 */
        public String text;
        /** 按页/章节切分的小段（PPT=每页，PDF=每页，Word=每段落组） */
        public List<String> sections = new ArrayList<>();
        /** 解析状态 */
        public boolean ok;
        /** 失败原因 */
        public String error;
    }

    public ParseResult parse(File file, String format) {
        ParseResult r = new ParseResult();
        try {
            String fmt = format != null ? format.toLowerCase() : detect(file.getName());
            switch (fmt) {
                case "pdf":    return parsePdf(file);
                case "docx":   return parseDocx(file);
                case "xlsx":   return parseXlsx(file);
                case "pptx":   return parsePptx(file);
                case "txt":    return parseTxt(file);
                case "csv":    return parseCsv(file);
                default:
                    r.ok = false;
                    r.error = "暂不支持的格式: " + fmt;
                    return r;
            }
        } catch (Exception e) {
            log.error("[ParseService] 解析失败: {}", file.getName(), e);
            r.ok = false;
            r.error = e.getMessage();
            return r;
        }
    }

    public static String detect(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        return i >= 0 ? fileName.substring(i + 1).toLowerCase() : "";
    }

    // ---- PDF ----
    private ParseResult parsePdf(File f) throws Exception {
        ParseResult r = new ParseResult();
        try (PDDocument doc = PDDocument.load(f)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder full = new StringBuilder();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String page = stripper.getText(doc).trim();
                if (!page.isEmpty()) {
                    r.sections.add("【第" + p + "页】\n" + clean(page));
                    full.append("【第").append(p).append("页】\n").append(clean(page)).append("\n\n");
                }
            }
            r.text = full.toString().trim();
            r.ok = !r.text.isEmpty();
        }
        return r;
    }

    // ---- Word (.docx) ----
    private ParseResult parseDocx(File f) throws Exception {
        ParseResult r = new ParseResult();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(f))) {
            StringBuilder full = new StringBuilder();
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph) {
                    String txt = ((XWPFParagraph) el).getText().trim();
                    if (!txt.isEmpty()) {
                        full.append(txt).append("\n");
                        r.sections.add(clean(txt));
                    }
                } else if (el instanceof XWPFTable) {
                    StringBuilder table = new StringBuilder();
                    for (XWPFTableRow row : ((XWPFTable) el).getRows()) {
                        StringBuilder line = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            line.append(cell.getText().trim()).append(" | ");
                        }
                        if (line.length() > 3) table.append(line.substring(0, line.length() - 3)).append("\n");
                    }
                    if (table.length() > 0) {
                        full.append(table);
                        r.sections.add(clean(table.toString()));
                    }
                }
            }
            r.text = full.toString().trim();
            r.ok = !r.text.isEmpty();
        }
        return r;
    }

    // ---- Excel (.xlsx) ----
    private ParseResult parseXlsx(File f) throws Exception {
        ParseResult r = new ParseResult();
        StringBuilder full = new StringBuilder();
        try (Workbook wb = WorkbookFactory.create(new FileInputStream(f))) {
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                String sheetName = sheet.getSheetName();
                StringBuilder sb = new StringBuilder("【工作表: " + sheetName + "】\n");
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        line.append(cellStr(cell)).append("\t");
                    }
                    if (!line.toString().trim().isEmpty()) sb.append(line).append("\n");
                }
                String section = sb.toString().trim();
                full.append(section).append("\n\n");
                if (!section.isEmpty()) r.sections.add(section);
            }
        }
        r.text = full.toString().trim();
        r.ok = !r.text.isEmpty();
        return r;
    }

    private String cellStr(Cell c) {
        if (c == null) return "";
        switch (c.getCellType()) {
            case STRING: return c.getStringCellValue().trim();
            case NUMERIC: return String.valueOf(c.getNumericCellValue());
            case BOOLEAN: return String.valueOf(c.getBooleanCellValue());
            case FORMULA: try { return c.getStringCellValue().trim(); } catch (Exception e) { return String.valueOf(c.getNumericCellValue()); }
            default: return "";
        }
    }

    // ---- PPT (.pptx) ----
    private ParseResult parsePptx(File f) throws Exception {
        ParseResult r = new ParseResult();
        StringBuilder full = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(f))) {
            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                StringBuilder sb = new StringBuilder();
                for (XSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        String txt = ((XSLFTextShape) shape).getText().trim();
                        if (!txt.isEmpty()) sb.append(txt).append("\n");
                    }
                }
                if (sb.length() > 0) {
                    String section = "【第" + (i + 1) + "页】\n" + sb.toString().trim();
                    r.sections.add(section);
                    full.append(section).append("\n\n");
                }
            }
        }
        r.text = full.toString().trim();
        r.ok = !r.text.isEmpty();
        return r;
    }

    // ---- TXT ----
    private ParseResult parseTxt(File f) throws Exception {
        ParseResult r = new ParseResult();
        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
        String content = new String(bytes, detectCharset(bytes));
        content = clean(content);
        List<String> paras = new ArrayList<>();
        for (String p : content.split("\n\\s*\n")) {
            String t = p.trim();
            if (!t.isEmpty()) paras.add(t);
        }
        r.sections = paras;
        r.text = content;
        r.ok = !content.isEmpty();
        return r;
    }

    private ParseResult parseCsv(File f) throws Exception { return parseTxt(f); }

    private Charset detectCharset(byte[] bytes) {
        // 简单检测 UTF-8 BOM
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) return StandardCharsets.UTF_8;
        return StandardCharsets.UTF_8;
    }

    /** 清理页眉页脚/水印/多空白等噪声 */
    private String clean(String s) {
        if (s == null) return "";
        return s
            .replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F]", "")  // 控制字符
            .replaceAll("^\\s*https?://.*$", "")                           // 单独一行的URL（大概率页眉页脚）
            .replaceAll("第\\s*\\d+\\s*页\\s*[/-]\\s*\\d+", "")            // "第 X 页 / Y"
            .replaceAll("\\n{3,}", "\n\n")                                  // 过多空行
            .trim();
    }
}
