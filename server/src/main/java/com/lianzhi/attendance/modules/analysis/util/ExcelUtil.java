package com.lianzhi.attendance.modules.analysis.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 数据分析模块专用的简易 Excel 导出工具
 * 用法：build("sheet名", 表头数组, 数据行二维数组) → 返回 xlsx 文件字节
 * 数据行的每个元素可以是 String / Number / null，null 输出空单元格
 */
public class ExcelUtil {

    /** 生成一个单 sheet 的 xlsx 文件字节流 */
    public static byte[] build(String sheetName, String[] headers, List<Object[]> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);

            // 表头样式：加粗
            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            // 写表头
            Row headRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headStyle);
            }

            // 写数据行
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object[] vals = rows.get(r);
                for (int i = 0; i < vals.length; i++) {
                    Cell c = row.createCell(i);
                    Object v = vals[i];
                    if (v == null) {
                        c.setCellValue("");
                    } else if (v instanceof Number) {
                        c.setCellValue(((Number) v).doubleValue());
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }

            // 自动列宽（限制最大 30 字符，避免超长列撑爆）
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, Math.min(30 * 256, (headers[i].length() + 8) * 256));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成Excel失败：" + e.getMessage(), e);
        }
    }
}
