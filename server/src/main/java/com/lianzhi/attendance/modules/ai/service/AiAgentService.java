package com.lianzhi.attendance.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.knowledge.entity.KnChunk;
import com.lianzhi.attendance.modules.knowledge.service.KnowledgeService;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 问答 Agent —— Phase 2：RAG + LLM 自然语言生成
 *
 * 架构：
 *   1. 从数据库拉全量结构化真实数据（考勤规则 / 今日概况 / 部门工时 / 审批 / 场所）
 *   2. 从知识库检索 topK 相关切片（TF-IDF 本地或 DashScope embedding）
 *   3. 拼 prompt → 调 DashScope qwen-turbo 生成自然语言回答
 *   4. 未配 api-key → 自动 fallback 到 Phase 1 规则化模板（零侵入）
 *
 * 这样用户用自然语言随便问都能答，不再受固定关键词限制。
 */
@Slf4j
@Service
public class AiAgentService {

    @Autowired CheckinService checkinService;
    @Autowired EmployeeService employeeService;
    @Autowired RuleService ruleService;
    @Autowired VenueService venueService;
    @Autowired ApprovalService approvalService;
    @Autowired(required = false) KnowledgeService knowledgeService;

    // ===== LLM 配置（支持多 provider：siliconflow / dashscope）=====
    @Value("${lianzhi.llm.provider:}")
    private String llmProvider;

    @Value("${lianzhi.llm.siliconflow.api-key:}")
    private String sfKey;
    @Value("${lianzhi.llm.siliconflow.chat-model:deepseek-ai/DeepSeek-V3.2}")
    private String sfModel;
    @Value("${lianzhi.llm.siliconflow.endpoint:https://api.siliconflow.cn/v1/chat/completions}")
    private String sfEndpoint;

    @Value("${lianzhi.llm.dashscope.api-key:}")
    private String dsKey;
    @Value("${lianzhi.llm.dashscope.chat-model:qwen-turbo}")
    private String dsModel;
    @Value("${lianzhi.llm.dashscope.endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String dsEndpoint;

    /** 根据当前 provider 返回实际生效的 key */
    private String currentKey() {
        if ("siliconflow".equalsIgnoreCase(llmProvider)) return sfKey;
        if ("dashscope".equalsIgnoreCase(llmProvider)) return dsKey;
        return null;
    }
    private String currentModel() {
        if ("siliconflow".equalsIgnoreCase(llmProvider)) return sfModel;
        if ("dashscope".equalsIgnoreCase(llmProvider)) return dsModel;
        return "qwen-turbo";
    }
    private String currentEndpoint() {
        if ("siliconflow".equalsIgnoreCase(llmProvider)) return sfEndpoint;
        if ("dashscope".equalsIgnoreCase(llmProvider)) return dsEndpoint;
        return "";
    }

    private final ObjectMapper json = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Data @AllArgsConstructor
    public static class Answer {
        private String content;
        private String sources;
    }

    /** LLM 是否可用 */
    private boolean llmAvailable() {
        boolean ok = ("siliconflow".equalsIgnoreCase(llmProvider) || "dashscope".equalsIgnoreCase(llmProvider))
                && currentKey() != null && !currentKey().isBlank();
        log.info("[AI] LLM 配置: provider='{}', keyPresent={}, keyLen={}, endpoint='{}', model='{}' → available={}",
                llmProvider, currentKey() != null && !currentKey().isBlank(),
                currentKey() != null ? currentKey().length() : 0,
                currentEndpoint(), currentModel(), ok);
        return ok;
    }

    // ============================================================
    //  主入口
    // ============================================================
    public Answer ask(String question, String userType, Long userId, Long empId) {
        String q = question.trim();
        List<Map<String, String>> sources = new ArrayList<>();

        // ===== Phase 2：LLM + RAG 模式 =====
        if (llmAvailable()) {
            try {
                // 0. 意图分类（规则类 → 不发审批/打卡记录，只发规则配置）
                boolean ruleIntent = isRuleIntent(q);
                log.info("[AI] 意图分类: {} → 规则类={}", q, ruleIntent);

                // 1. 收集结构化数据（根据意图过滤）
                String structured = collectStructuredContext(userType, empId, sources, ruleIntent);
                // 2. 知识库检索（规则类问题强制必须检索到）
                String kb = retrieveKnowledgeText(q, sources);
                // 3. 调 LLM 生成
                String reply = callLLM(q, structured, kb);
                if (reply != null && !reply.isBlank()) {
                    // 4. 追加知识库来源引用
                    reply = appendKnowledgeRAG(q, reply, sources);
                    return buildAnswer(reply, sources);
                }
                log.warn("[AI] LLM 返回为空，fallback 规则模板");
            } catch (Exception e) {
                log.warn("[AI] LLM 调用失败，fallback 规则模板: {}", e.getMessage());
            }
        }

        // ===== Phase 1：规则化模板兜底 =====
        String reply = route(q, userType, userId, empId, sources);
        reply = appendKnowledgeRAG(q, reply, sources);
        return buildAnswer(reply, sources);
    }

    /**
     * 判断是否是"规则类/定义类"意图
     * 规则类 = 应该优先从知识库找制度原文，不需要 DB 里的审批/打卡记录
     * FACT类 = 查询类/统计类，需要真实 DB 数据
     */
    private boolean isRuleIntent(String q) {
        String text = q.toLowerCase();
        // 规则类关键词
        String[] ruleKw = {"分为", "几类", "类型", "定义", "规定", "规则", "制度", "怎么算", "怎么界定",
                "什么意思", "流程", "怎么做", "怎么办理", "怎么处罚", "怎么认定", "豁免", "条件",
                "有效期", "多久", "多少天", "多少小时", "提前", "范围", "标准", "要求",
                "可以吗", "能不能", "允许", "禁止", "处罚", "算不算", "属于", "等同于",
                "制度原文", "条款", "细则", "政策", "规范", "标准工作时间", "考勤制度"};
        for (String kw : ruleKw) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private Answer buildAnswer(String content, List<Map<String, String>> sources) {
        try {
            return new Answer(content, sources.isEmpty() ? null : json.writeValueAsString(sources));
        } catch (Exception e) {
            return new Answer(content, null);
        }
    }

    // ============================================================
    //  Phase 2: LLM + RAG 核心
    // ============================================================

    /** 收集所有可能和考勤相关的结构化真实数据（LLM 自己挑选用哪些） */
    private String collectStructuredContext(String userType, Long empId, List<Map<String, String>> sources, boolean ruleIntentOnly) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.now();
        sb.append("当前时间：").append(today.format(FMT_DATE)).append("\n\n");

        // --- 1. 考勤规则配置（规则类 + 事实类 都需要） ---
        try {
            List<AttendRule> rules = ruleService.list();
            if (!rules.isEmpty()) {
                sb.append("【考勤规则配置】\n");
                for (AttendRule r : rules) {
                    String vName = "默认";
                    if (r.getVenueId() != null) {
                        Venue v = venueService.getById(r.getVenueId());
                        if (v != null) vName = v.getName();
                    }
                    sb.append(String.format("- %s: 上班%s 下班%s 迟到宽限%d分钟\n",
                            vName,
                            r.getWorkStart() != null ? r.getWorkStart() : "09:00",
                            r.getWorkEnd() != null ? r.getWorkEnd() : "18:00",
                            r.getLateMinutes() != null ? r.getLateMinutes() : 10));
                }
                sb.append("\n");
                sources.add(src("attend_rule", "考勤规则"));
            }
        } catch (Exception ignore) {}

        // --- 2. 今日概况 ---
        if (!ruleIntentOnly) { try {
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
            long totalEmp = employeeService.count(Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));
            List<CheckinRecord> todayRecords = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                    .ge(CheckinRecord::getSignInTime, dayStart).lt(CheckinRecord::getSignInTime, dayEnd));
            long checkedIn = todayRecords.stream().map(CheckinRecord::getEmpId).distinct().count();
            long lateCount = todayRecords.stream().filter(r -> "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
            long earlyCount = todayRecords.stream().filter(r -> "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
            long absentCount = Math.max(totalEmp - checkedIn, 0);

            sb.append("【今日考勤概况】\n");
            sb.append(String.format("- 在职员工%d人 已打卡%d人 未打卡%d人\n", totalEmp, checkedIn, absentCount));
            sb.append(String.format("- 迟到%d人次 早退%d人次\n", lateCount, earlyCount));

            // 迟到人员
            List<CheckinRecord> lates = todayRecords.stream().filter(r -> "late".equals(r.getStatus())).collect(Collectors.toList());
            if (!lates.isEmpty()) {
                sb.append("- 迟到人员: ");
                sb.append(lates.stream().limit(10).map(r -> {
                    Employee e = employeeService.getById(r.getEmpId());
                    return e != null ? e.getName() + "(" + r.getSignInTime().format(FMT) + ")" : "emp#" + r.getEmpId();
                }).collect(Collectors.joining(", ")));
                if (lates.size() > 10) sb.append(" 等").append(lates.size()).append("人");
                sb.append("\n");
            }

            // 未打卡人员
            if (absentCount > 0) {
                Set<Long> todayEmpIds = todayRecords.stream().map(CheckinRecord::getEmpId).collect(Collectors.toSet());
                List<Employee> absents = employeeService.list(Wrappers.<Employee>lambdaQuery()
                        .eq(Employee::getStatus, 1)
                        .notIn(Employee::getId, todayEmpIds)
                        .last("LIMIT 10"));
                if (!absents.isEmpty()) {
                    sb.append("- 未打卡人员: ").append(absents.stream().map(Employee::getName).collect(Collectors.joining(", ")));
                    if (absentCount > 10) sb.append(" 等").append(absentCount).append("人");
                    sb.append("\n");
                }
            }
            sb.append("\n");
            sources.add(src("checkin_record", "今日打卡"));
        } catch (Exception ignore) {} }
        // --- 3. 本月部门工时 ---
        if (!ruleIntentOnly) { try {
            LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
            LocalDateTime monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay();
            List<CheckinRecord> monthRecords = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                    .ge(CheckinRecord::getSignInTime, monthStart).lt(CheckinRecord::getSignInTime, monthEnd));
            if (!monthRecords.isEmpty()) {
                List<Long> empIds = monthRecords.stream().map(CheckinRecord::getEmpId).distinct().collect(Collectors.toList());
                Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                        .collect(Collectors.toMap(Employee::getId, e -> e));

                Map<String, long[]> deptMap = new LinkedHashMap<>();
                for (CheckinRecord r : monthRecords) {
                    Employee e = empMap.get(r.getEmpId());
                    if (e == null) continue;
                    String dept = e.getDept() != null ? e.getDept() : "未分配";
                    deptMap.computeIfAbsent(dept, k -> new long[2]);
                    deptMap.get(dept)[0]++; // 人次
                    if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                        deptMap.get(dept)[1] += Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
                    }
                }
                sb.append("【本月部门工时】\n");
                deptMap.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                        .forEach(en -> {
                            BigDecimal totalH = BigDecimal.valueOf(en.getValue()[1]).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
                            sb.append(String.format("- %s: 总工时%s h (%d人次)\n", en.getKey(), totalH, en.getValue()[0]));
                        });
                sb.append("\n");
                sources.add(src("checkin_record", "本月工时"));
            }
        } catch (Exception ignore) {} }

        // --- 4. 场所 ---
        try {
            List<Venue> venues = venueService.list(Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1));
            if (!venues.isEmpty()) {
                sb.append("【办公场所】\n");
                for (Venue v : venues) {
                    sb.append(String.format("- %s: %s 地址:%s\n",
                            v.getName(),
                            v.getCompanyName() != null ? v.getCompanyName() : "-",
                            v.getAddress() != null ? v.getAddress() : "-"));
                }
                sb.append("\n");
                sources.add(src("venue", "场所列表"));
            }
        } catch (Exception ignore) {}

        // --- 5. 审批记录（规则类不要！！） ---
        if (!ruleIntentOnly) { try {
            List<ApprovalRecord> approvals;
            if ("h5".equals(userType) && empId != null) {
                approvals = approvalService.list(Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getEmpId, empId).orderByDesc(ApprovalRecord::getCreateTime).last("LIMIT 10"));
            } else {
                approvals = approvalService.list(Wrappers.<ApprovalRecord>lambdaQuery()
                        .orderByDesc(ApprovalRecord::getCreateTime).last("LIMIT 10"));
            }
            if (!approvals.isEmpty()) {
                sb.append("【审批记录（最近10条）】\n");
                for (ApprovalRecord r : approvals) {
                    String name = empId != null ? "你" : (employeeService.getById(r.getEmpId()) != null ? employeeService.getById(r.getEmpId()).getName() : "员工" + r.getEmpId());
                    sb.append(String.format("- %s %s %s → %s (%s) %s\n",
                            name, approvalType(r.getType()),
                            r.getStartTime() != null ? r.getStartTime().format(FMT_DATE) : "-",
                            r.getEndTime() != null ? r.getEndTime().format(FMT_DATE) : "-",
                            r.getDurationHours() != null ? r.getDurationHours() + "h" : "-",
                            approvalStatus(r.getStatus())));
                }
                sb.append("\n");
                sources.add(src("approval_record", "审批记录"));
            }
        } catch (Exception ignore) {} }

        // --- 6. H5 用户个人考勤 ---
        if (!ruleIntentOnly && "h5".equals(userType) && empId != null) {
            try {
                LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
                LocalDateTime monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay();
                List<CheckinRecord> mine = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, empId)
                        .ge(CheckinRecord::getSignInTime, monthStart).lt(CheckinRecord::getSignInTime, monthEnd));
                long attendDays = mine.stream().filter(r -> r.getSignInTime() != null).count();
                long lateMine = mine.stream().filter(r -> "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
                long earlyMine = mine.stream().filter(r -> "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
                double totalH = 0;
                for (CheckinRecord r : mine) {
                    if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                        totalH += Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes() / 60.0;
                    }
                }
                Employee me = employeeService.getById(empId);
                sb.append("【本月个人考勤").append(me != null ? "（" + me.getName() + "）" : "").append("】\n");
                sb.append(String.format("- 出勤%d天 迟到%d次 早退%d次 累计工时%.1fh\n",
                        attendDays, lateMine, earlyMine, totalH));
                sb.append("\n");
                sources.add(src("checkin_record", "个人月度"));
            } catch (Exception ignore) {}
        }

        return sb.toString();
    }

    /** 知识库检索，返回纯文本片段 */
    private String retrieveKnowledgeText(String query, List<Map<String, String>> sources) {
        if (knowledgeService == null) return "";
        try {
            List<KnowledgeService.RetrievedChunk> refs = knowledgeService.retrieve(query, 4, 0.15);
            if (refs == null || refs.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("【内部知识库参考】\n");
            for (int i = 0; i < refs.size(); i++) {
                KnowledgeService.RetrievedChunk r = refs.get(i);
                KnChunk c = r.chunk;
                String content = c.getContent();
                if (content != null && content.length() > 500) content = content.substring(0, 500) + "...";
                sb.append(String.format("%d. [%s] %s\n", i + 1,
                        c.getTitle() != null ? c.getTitle() : "片段",
                        content));
                sources.add(src("knowledge_chunk", "知识库片段id=" + c.getId() + "相似度=" + String.format("%.2f", r.similarity)));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[RAG] 知识库检索异常: {}", e.getMessage());
            return "";
        }
    }

    /** 调 LLM 生成回答（OpenAI 兼容格式，所有 provider 通用，带 429 限流重试） */
    private String callLLM(String question, String structuredContext, String knowledge) {
        String systemPrompt = """
                你是「联智智能考勤系统」的AI助手。以下给你两种信息源：

                【内部知识库】= 公司考勤制度原文、规定条款、政策解释
                  → 回答"规则类/定义类"问题时**必须优先使用知识库**
                  → 例：请假有几类、迟到怎么算、加班可以调休吗、年假几天、补卡流程、旷工怎么处罚

                【系统真实数据】= 考勤规则配置、今日打卡数据、审批记录、部门工时等
                  → 回答"查询类/事实类"问题时使用真实数据
                  → 例：今天谁迟到了、我最近有什么审批、本月哪个部门加班多、现在几点下班

                判断规则（极其重要）：
                - 用户问"XX分为哪几类"、"XX怎么定义"、"XX的规定是"、"XX的流程" → 知识库优先！
                - 用户问"我/今天/最近/本月" + 动作词 → 用系统真实数据
                - 知识库和真实数据不矛盾时，两者结合回答；矛盾时以知识库（制度原文）为准

                回答格式：
                1. 先直接回答问题，用中文，简洁清晰
                2. 涉及数字/规则的地方加粗
                3. 不要提及"数据来源"、"上下文"、"prompt"、"知识库"等术语
                4. 如果两个信息源都没答案，直说"我现在没有这方面的数据"
                """;

        StringBuilder userMsg = new StringBuilder();
        if (!knowledge.isBlank()) {
            userMsg.append("\n===== 【内部知识库】（制度原文，规则类问题请优先使用）=====\n").append(knowledge);
        }
        if (!structuredContext.isBlank()) {
            userMsg.append("\n===== 【系统真实数据】（实时查询数据）=====\n").append(structuredContext);
        }
        userMsg.append("\n===== 用户问题 =====\n").append(question);

        String model = currentModel();
        String endpoint = currentEndpoint();
        String key = currentKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMsg.toString())
        ));
        body.put("temperature", 0.3);
        body.put("max_tokens", 1024);

        // 最多重试 3 次（限流 429 或网络抖动）
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("[LLM] 调用中... attempt={}/{}. model={}. provider={}", attempt, 3, model, llmProvider);
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Content-Type", "application/json");

                byte[] payload = json.writeValueAsBytes(body);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload); }

                int code = conn.getResponseCode();
                if (code == 429) {
                    long wait = 1000L * attempt;
                    log.warn("[LLM] 429 限流，等待 {}ms 后重试", wait);
                    Thread.sleep(wait);
                    continue;
                }
                if (code == 401) {
                    log.warn("[LLM] 401 认证失败，请检查 api-key 是否正确");
                    return null;
                }
                if (code != 200) {
                    String err = "";
                    try { err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8); } catch (Exception ignore) {}
                    log.warn("[LLM] HTTP {}: {}", code, err);
                    return null;
                }

                String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> map = json.readValue(resp, new TypeReference<>() {});
                List<Map<String, Object>> choices = (List) map.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map) choices.get(0).get("message");
                    String content = (String) msg.get("content");
                    log.info("[LLM] ✅ 调用成功，返回 {} 字", content != null ? content.length() : 0);
                    return content;
                }
                return null;
            } catch (Exception e) {
                log.warn("[LLM] attempt={} 异常: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(500L * attempt); } catch (Exception ignore) {}
                }
            }
        }
        log.warn("[LLM] 3 次重试都失败，fallback 规则模板");
        return null;
    }

    // ============================================================
    //  Phase 1: 规则化模板（保留作 fallback）
    // ============================================================

    /** 意图路由（Phase 1 兜底用） */
    private String route(String q, String userType, Long userId, Long empId, List<Map<String, String>> sources) {
        if (containsAny(q, "上班时间", "几点上班", "下班时间", "几点下班", "考勤规则", "宽限", "迟到几分钟", "迟到多久算")) {
            return ruleQuery(q, sources);
        }
        if (containsAny(q, "今日考勤", "今天考勤", "今日概况", "今天概况", "今天打卡", "今日打卡")) {
            return todayOverview(sources);
        }
        if (containsAny(q, "迟到", "谁迟到", "迟到人员", "今天迟到", "本月迟到", "早退", "缺勤", "没打卡", "未打卡")) {
            return statusQuery(q, sources);
        }
        if (containsAny(q, "部门", "各部门", "工时", "部门统计", "部门分析")) {
            return deptHours(q, sources);
        }
        if (containsAny(q, "员工", "谁", "某员工") || looksLikeNameQuery(q)) {
            return employeeQuery(q, sources);
        }
        if (containsAny(q, "请假", "申请", "审批", "年假", "病假", "加班", "待审批")) {
            return approvalQuery(q, userType, userId, empId, sources);
        }
        if (containsAny(q, "场所", "地点", "办公点", "办公场所", "公司在哪")) {
            return venueQuery(sources);
        }
        if ((userType.equals("h5") || empId != null) && containsAny(q, "本月", "我这个月", "我的考勤", "我的统计", "我这个月加班", "这个月我")) {
            return personalMonth(empId, sources);
        }
        return fallback();
    }

    private String appendKnowledgeRAG(String query, String reply, List<Map<String, String>> sources) {
        String kb = retrieveKnowledgeText(query, sources);
        if (kb.isBlank()) return reply;
        return reply + "\n\n📚 " + kb.replace("【内部知识库参考】", "知识库参考：");
    }

    /** 考勤规则问答 */
    private String ruleQuery(String q, List<Map<String, String>> sources) {
        List<AttendRule> rules = ruleService.list();
        if (rules.isEmpty()) {
            AttendRule def = new AttendRule();
            def.setWorkStart(LocalTime.of(9, 0));
            def.setWorkEnd(LocalTime.of(18, 0));
            def.setLateMinutes(10);
            rules.add(def);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📋 当前配置的考勤规则如下：\n\n");
        Set<Long> seenVenue = new HashSet<>();
        for (AttendRule r : rules) {
            String vName = "默认规则";
            if (r.getVenueId() != null && !seenVenue.contains(r.getVenueId())) {
                seenVenue.add(r.getVenueId());
                Venue v = venueService.getById(r.getVenueId());
                if (v != null) vName = v.getName();
            }
            sb.append(String.format("• **%s**：上班 %s，下班 %s，迟到宽限 %d 分钟\n",
                    vName,
                    r.getWorkStart() != null ? r.getWorkStart().toString() : "09:00",
                    r.getWorkEnd() != null ? r.getWorkEnd().toString() : "18:00",
                    r.getLateMinutes() != null ? r.getLateMinutes() : 10));
        }
        sources.add(src("attend_rule", "场所考勤规则配置"));
        return sb.toString();
    }

    /** 今日考勤概况 */
    private String todayOverview(List<Map<String, String>> sources) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long totalEmployees = employeeService.count(Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));
        List<CheckinRecord> todayRecords = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));

        long checkedIn = todayRecords.stream().map(CheckinRecord::getEmpId).distinct().count();
        long lateCount = todayRecords.stream().filter(r ->
                "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        long earlyCount = todayRecords.stream().filter(r ->
                "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();

        StringBuilder sb = new StringBuilder();
        sb.append("📅 **").append(today.format(DateTimeFormatter.ISO_DATE)).append(" 考勤概况**\n\n");
        sb.append(String.format("• 在职员工：**%d** 人\n", totalEmployees));
        sb.append(String.format("• 已打卡：**%d** 人\n", checkedIn));
        sb.append(String.format("• 未打卡：**%d** 人\n", Math.max(totalEmployees - checkedIn, 0)));
        sb.append(String.format("• 迟到：**%d** 人次\n", lateCount));
        sb.append(String.format("• 早退：**%d** 人次\n", earlyCount));

        List<CheckinRecord> lates = todayRecords.stream().filter(r -> "late".equals(r.getStatus())).collect(Collectors.toList());
        if (!lates.isEmpty()) {
            sb.append("\n**迟到人员**：\n");
            appendEmpNames(sb, lates, "签到时间");
        }

        sources.add(src("checkin_record", "今日打卡数据"));
        sources.add(src("employee", "员工总数"));
        return sb.toString();
    }

    /** 迟到 / 早退 / 缺勤查询 */
    private String statusQuery(String q, List<Map<String, String>> sources) {
        LocalDateTime start; LocalDateTime end; String period;
        if (containsAny(q, "本月", "这个月")) {
            YearMonth ym = YearMonth.now();
            start = ym.atDay(1).atStartOfDay(); end = ym.plusMonths(1).atDay(1).atStartOfDay(); period = ym.toString();
        } else {
            LocalDate today = LocalDate.now();
            start = today.atStartOfDay(); end = today.plusDays(1).atStartOfDay(); period = today.format(DateTimeFormatter.ISO_DATE);
        }
        List<CheckinRecord> records = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **").append(period).append(" 考勤异常**\n\n");
        if (containsAny(q, "迟到")) {
            List<CheckinRecord> lates = records.stream().filter(r -> "late".equals(r.getStatus())).collect(Collectors.toList());
            sb.append(String.format("🚨 迟到 **%d** 人次\n", lates.size()));
            if (!lates.isEmpty()) appendEmpNames(sb, lates, "签到时间");
        }
        if (containsAny(q, "早退")) {
            List<CheckinRecord> earlys = records.stream().filter(r -> "early".equals(r.getStatus())).collect(Collectors.toList());
            sb.append(String.format("\n⚠️ 早退 **%d** 人次\n", earlys.size()));
            if (!earlys.isEmpty()) appendEmpNames(sb, earlys, "签退时间");
        }
        if (containsAny(q, "未打卡", "没打卡", "缺勤")) {
            List<Employee> all = employeeService.list(Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));
            Set<Long> checkedEmpIds = records.stream().map(CheckinRecord::getEmpId).collect(Collectors.toSet());
            List<Employee> absents = all.stream().filter(e -> !checkedEmpIds.contains(e.getId())).collect(Collectors.toList());
            sb.append(String.format("\n❌ 未打卡 **%d** 人\n", absents.size()));
            for (Employee e : absents) sb.append(String.format("• %s（%s）\n", e.getName(), e.getDept() != null ? e.getDept() : "未分配部门"));
        }
        sources.add(src("checkin_record", "考勤异常数据"));
        return sb.toString();
    }

    /** 部门工时分析 */
    private String deptHours(String q, List<Map<String, String>> sources) {
        LocalDateTime start; LocalDateTime end; String period;
        if (containsAny(q, "本月", "这个月")) {
            YearMonth ym = YearMonth.now();
            start = ym.atDay(1).atStartOfDay(); end = ym.plusMonths(1).atDay(1).atStartOfDay(); period = ym.toString();
        } else {
            LocalDate today = LocalDate.now();
            start = today.atStartOfDay(); end = today.plusDays(1).atStartOfDay(); period = today.format(DateTimeFormatter.ISO_DATE);
        }
        List<CheckinRecord> records = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));
        List<Long> empIds = records.stream().map(CheckinRecord::getEmpId).distinct().collect(Collectors.toList());
        Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<String, DeptStat> deptMap = new LinkedHashMap<>();
        for (CheckinRecord r : records) {
            Employee e = empMap.get(r.getEmpId()); if (e == null) continue;
            String dept = e.getDept() != null ? e.getDept() : "未分配部门";
            DeptStat stat = deptMap.computeIfAbsent(dept, k -> new DeptStat(dept));
            stat.empIds.add(r.getEmpId());
            if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                stat.totalMinutes += Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **").append(period).append(" 部门工时分析**\n\n");
        if (deptMap.isEmpty()) { sb.append("暂无打卡数据。\n"); }
        else {
            List<DeptStat> sorted = deptMap.values().stream()
                    .sorted((a, b) -> Long.compare(b.totalMinutes, a.totalMinutes)).collect(Collectors.toList());
            for (DeptStat ds : sorted) {
                BigDecimal totalH = BigDecimal.valueOf(ds.totalMinutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
                BigDecimal avgH = ds.empIds.isEmpty() ? BigDecimal.ZERO :
                        totalH.divide(BigDecimal.valueOf(ds.empIds.size()), 1, RoundingMode.HALF_UP);
                sb.append(String.format("**%s**：总工时 %s h，人均 %s h（%d 人参与）\n", ds.name, totalH, avgH, ds.empIds.size()));
            }
        }
        sources.add(src("checkin_record", "打卡工时数据"));
        return sb.toString();
    }

    /** 员工查询 */
    private String employeeQuery(String q, List<Map<String, String>> sources) {
        String nameKey = extractNameKeyword(q);
        LambdaQueryWrapper<Employee> w = Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1);
        if (nameKey != null && !nameKey.isEmpty()) w.like(Employee::getName, nameKey);
        List<Employee> employees = employeeService.list(w);
        if (employees.isEmpty()) return "抱歉，没找到匹配的员工信息。";
        StringBuilder sb = new StringBuilder();
        sb.append("👥 查到 **").append(employees.size()).append("** 位员工：\n\n");
        for (Employee e : employees) {
            sb.append(String.format("• **%s**｜工号 %s｜%s｜%s｜%s\n", e.getName(),
                    e.getEmpNo() != null ? e.getEmpNo() : "-",
                    e.getGender() != null ? e.getGender() : "-",
                    e.getDept() != null ? e.getDept() : "未分配部门",
                    e.getPhone() != null ? e.getPhone() : "-"));
        }
        sources.add(src("employee", "员工档案"));
        return sb.toString();
    }

    /** 请假 / 审批查询 */
    private String approvalQuery(String q, String userType, Long userId, Long empId, List<Map<String, String>> sources) {
        List<ApprovalRecord> records;
        if ("h5".equals(userType) && empId != null) {
            records = approvalService.list(Wrappers.<ApprovalRecord>lambdaQuery()
                    .eq(ApprovalRecord::getEmpId, empId).orderByDesc(ApprovalRecord::getCreateTime).last("LIMIT 10"));
        } else {
            records = approvalService.list(Wrappers.<ApprovalRecord>lambdaQuery()
                    .orderByDesc(ApprovalRecord::getCreateTime).last("LIMIT 10"));
        }
        if (records.isEmpty()) return "📝 当前没有审批记录。";
        StringBuilder sb = new StringBuilder();
        sb.append("📝 最近 **").append(records.size()).append("** 条审批记录：\n\n");
        for (ApprovalRecord r : records) {
            String empName = "-";
            Employee emp = employeeService.getById(r.getEmpId());
            if (emp != null) empName = emp.getName();
            sb.append(String.format("• %s｜**%s**｜%s → %s（%s）｜%s\n",
                    approvalType(r.getType()), empName,
                    r.getStartTime() != null ? r.getStartTime().format(FMT) : "-",
                    r.getEndTime() != null ? r.getEndTime().format(FMT) : "-",
                    r.getDurationHours() != null ? r.getDurationHours() + "h" : "-",
                    approvalStatus(r.getStatus())));
        }
        sources.add(src("approval_record", "审批记录"));
        return sb.toString();
    }

    /** 场所查询 */
    private String venueQuery(List<Map<String, String>> sources) {
        List<Venue> venues = venueService.list(Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1));
        if (venues.isEmpty()) return "当前没有配置办公场所。";
        StringBuilder sb = new StringBuilder();
        sb.append("🏢 已配置的办公场所：\n\n");
        for (Venue v : venues) {
            sb.append(String.format("• **%s**｜%s｜📍 %s\n",
                    v.getName(), v.getCompanyName() != null ? v.getCompanyName() : "-",
                    v.getAddress() != null ? v.getAddress() : "-"));
        }
        sources.add(src("venue", "办公场所列表"));
        return sb.toString();
    }

    /** H5 用户本月个人统计 */
    private String personalMonth(Long empId, List<Map<String, String>> sources) {
        if (empId == null) return "请先完成员工信息采集后再查询个人考勤。";
        YearMonth ym = YearMonth.now();
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        List<CheckinRecord> records = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .eq(CheckinRecord::getEmpId, empId)
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));
        long attendDays = records.stream().filter(r -> r.getSignInTime() != null).count();
        long lateCount = records.stream().filter(r -> "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        long earlyCount = records.stream().filter(r -> "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        double totalHours = 0;
        for (CheckinRecord r : records) {
            if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                totalHours += Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes() / 60.0;
            }
        }
        Employee emp = employeeService.getById(empId);
        String empName = emp != null ? emp.getName() : "你";
        StringBuilder sb = new StringBuilder();
        sb.append("📅 **").append(empName).append(" · ").append(ym.toString()).append(" 个人考勤**\n\n");
        sb.append(String.format("• 出勤天数：**%d** 天\n", attendDays));
        sb.append(String.format("• 迟到 **%d** 次\n", lateCount));
        sb.append(String.format("• 早退 **%d** 次\n", earlyCount));
        sb.append(String.format("• 累计工时：**%.2f** 小时\n", totalHours));
        sources.add(src("checkin_record", "个人月度打卡数据"));
        return sb.toString();
    }

    private String fallback() {
        return llmAvailable() ? "抱歉，我暂时没太理解你的问题 😅\n你可以换个说法再问我。" :
                """
                抱歉，我暂时没太理解你的问题 😅
                你可以这样问我：
                • 今日考勤概况
                • 今天谁迟到了
                • 公司上班时间是几点
                • 销售部这个月工时多少
                • 最近有哪些请假申请
                • 本月我的考勤统计
                """;
    }

    // ===================== 工具方法 =====================
    private static boolean containsAny(String s, String... kws) {
        for (String k : kws) if (s.contains(k)) return true;
        return false;
    }
    private static boolean looksLikeNameQuery(String s) {
        return s.matches(".*叫.+.") || s.matches(".*是.+") || s.matches(".*查.+");
    }
    private static String extractNameKeyword(String s) {
        String cleaned = s.replaceAll("[今天|这个月|本月|谁|查询|查|的|员工|人员|人员|情况|状态]", "").trim();
        if (cleaned.length() >= 2 && cleaned.length() <= 4) return cleaned;
        return null;
    }
    private static Map<String, String> src(String table, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("table", table); m.put("desc", desc);
        m.put("retrieved_at", LocalDateTime.now().format(FMT));
        return m;
    }
    private void appendEmpNames(StringBuilder sb, List<CheckinRecord> records, String timeField) {
        List<Long> empIds = records.stream().map(CheckinRecord::getEmpId).distinct().collect(Collectors.toList());
        Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        for (CheckinRecord r : records) {
            Employee e = empMap.get(r.getEmpId());
            String name = e != null ? e.getName() : "员工" + r.getEmpId();
            String dept = e != null && e.getDept() != null ? "（" + e.getDept() + "）" : "";
            LocalDateTime t = "签退时间".equals(timeField) ? r.getSignOutTime() : r.getSignInTime();
            sb.append(String.format("• %s%s · %s\n", name, dept, t != null ? t.format(FMT) : "-"));
        }
    }
    private static String approvalType(String type) {
        if (type == null) return "申请";
        return switch (type) {
            case "leave" -> "请假"; case "sick" -> "病假"; case "annual" -> "年假";
            case "overtime" -> "加班"; case "compensate" -> "补休"; case "makeup" -> "补卡";
            case "resign" -> "离职"; default -> type;
        };
    }
    private static String approvalStatus(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "pending" -> "待审批"; case "approved" -> "已通过";
            case "rejected" -> "已驳回"; case "cancelled" -> "已撤回";
            default -> status;
        };
    }

    private static class DeptStat {
        String name;
        Set<Long> empIds = new HashSet<>();
        long totalMinutes = 0;
        DeptStat(String n) { this.name = n; }
    }
}
