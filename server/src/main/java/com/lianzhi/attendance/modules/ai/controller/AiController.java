package com.lianzhi.attendance.modules.ai.controller;

import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.ai.entity.AiMessage;
import com.lianzhi.attendance.modules.ai.entity.AiSession;
import com.lianzhi.attendance.modules.ai.service.AiAgentService;
import com.lianzhi.attendance.modules.ai.service.AiMessageService;
import com.lianzhi.attendance.modules.ai.service.AiSessionService;
import com.lianzhi.attendance.modules.auth.entity.SysUser;
import com.lianzhi.attendance.modules.auth.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/** 管理端 AI 助手 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired AiSessionService sessionService;
    @Autowired AiMessageService messageService;
    @Autowired AiAgentService agent;
    @Autowired SysUserMapper userMapper;

    private final ObjectMapper json = new ObjectMapper();

    /** 新建会话 */
    @PostMapping("/session/new")
    public Result<AiSession> newSession(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        AiSession s = sessionService.create(userId, "admin");
        return Result.ok(s);
    }

    /** 我的会话列表 */
    @GetMapping("/session/list")
    public Result<List<AiSession>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(sessionService.list(userId, "admin"));
    }

    /** 会话消息历史 */
    @GetMapping("/session/{id}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (!sessionService.belongsTo(id, userId, "admin")) return Result.fail("无权限");
        List<AiMessage> msgs = messageService.all(id);
        List<Map<String, Object>> out = msgs.stream().map(m -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("role", m.getRole());
            o.put("content", m.getContent());
            o.put("sources", m.getSources());
            o.put("createTime", m.getCreateTime());
            return o;
        }).collect(Collectors.toList());
        return Result.ok(out);
    }

    /** 发送消息 → Agent 回答 → 存库 → 返回 */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long sessionId = Long.valueOf(body.get("sessionId"));
        String question = body.get("question");

        if (!sessionService.belongsTo(sessionId, userId, "admin")) return Result.fail("无权限");

        // 1. 存用户消息
        messageService.saveUser(sessionId, question);

        // 2. Agent 回答
        AiAgentService.Answer ans = agent.ask(question, "admin", userId, null);

        // 3. 存助手消息
        messageService.saveAssistant(sessionId, ans.getContent(), ans.getSources());

        // 4. 会话标题自动取首问前 20 字（如果还是默认标题）
        AiSession s = sessionService.get(sessionId);
        if ("新对话".equals(s.getTitle())) {
            String t = question.length() > 20 ? question.substring(0, 20) + "…" : question;
            sessionService.rename(sessionId, t);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("reply", ans.getContent());
        out.put("sources", ans.getSources());
        return Result.ok(out);
    }

    /** 删除会话（同时删其所有消息） */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (!sessionService.belongsTo(id, userId, "admin")) return Result.fail("无权限");
        messageService.deleteBySession(id);
        sessionService.delete(id);
        return Result.ok();
    }

    /** 导出会话 Markdown */
    @GetMapping("/session/{id}/export")
    public Result<Map<String, Object>> export(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (!sessionService.belongsTo(id, userId, "admin")) return Result.fail("无权限");
        AiSession s = sessionService.get(id);
        List<AiMessage> msgs = messageService.all(id);

        StringBuilder md = new StringBuilder();
        md.append("# ").append(s.getTitle()).append("\n\n");
        md.append("> 会话时间：").append(s.getCreateTime()).append(" ｜ 消息数：").append(msgs.size()).append("\n\n---\n\n");
        for (AiMessage m : msgs) {
            String roleLabel = "user".equals(m.getRole()) ? "👤 用户" : "🤖 AI";
            md.append("### ").append(roleLabel).append("\n\n");
            md.append(m.getContent()).append("\n\n");
            if (m.getSources() != null && !m.getSources().isEmpty()) {
                md.append("**来源引用**：\n\n```json\n").append(m.getSources()).append("\n```\n\n");
            }
            md.append("---\n\n");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("filename", (s.getTitle() != null ? s.getTitle() : "对话") + ".md");
        out.put("markdown", md.toString());
        return Result.ok(out);
    }
}
