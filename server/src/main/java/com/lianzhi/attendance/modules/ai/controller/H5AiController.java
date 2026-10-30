package com.lianzhi.attendance.modules.ai.controller;

import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.ai.entity.AiMessage;
import com.lianzhi.attendance.modules.ai.entity.AiSession;
import com.lianzhi.attendance.modules.ai.service.AiAgentService;
import com.lianzhi.attendance.modules.ai.service.AiMessageService;
import com.lianzhi.attendance.modules.ai.service.AiSessionService;
import com.lianzhi.attendance.modules.h5.entity.H5Account;
import com.lianzhi.attendance.modules.h5.mapper.H5AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/** H5 员工端 AI 助手 */
@RestController
@RequestMapping("/h5/ai")
public class H5AiController {

    @Autowired AiSessionService sessionService;
    @Autowired AiMessageService messageService;
    @Autowired AiAgentService agent;
    @Autowired H5AccountMapper accountMapper;

    /** 从 request 里取当前 H5 账号 */
    private H5Account currentAccount(HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("h5AccountId");
        return accountId == null ? null : accountMapper.selectById(accountId);
    }

    /** 新建会话 */
    @PostMapping("/session/new")
    public Result<AiSession> newSession(HttpServletRequest request) {
        H5Account acc = currentAccount(request);
        if (acc == null) return Result.fail("请先登录");
        return Result.ok(sessionService.create(acc.getId(), "h5"));
    }

    /** 我的会话列表 */
    @GetMapping("/session/list")
    public Result<List<AiSession>> list(HttpServletRequest request) {
        H5Account acc = currentAccount(request);
        if (acc == null) return Result.fail("请先登录");
        return Result.ok(sessionService.list(acc.getId(), "h5"));
    }

    /** 会话消息历史 */
    @GetMapping("/session/{id}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable Long id, HttpServletRequest request) {
        H5Account acc = currentAccount(request);
        if (acc == null) return Result.fail("请先登录");
        if (!sessionService.belongsTo(id, acc.getId(), "h5")) return Result.fail("无权限");
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

    /** 发送消息 → Agent 回答 */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        H5Account acc = currentAccount(request);
        if (acc == null) return Result.fail("请先登录");

        Long sessionId = Long.valueOf(body.get("sessionId"));
        String question = body.get("question");
        if (!sessionService.belongsTo(sessionId, acc.getId(), "h5")) return Result.fail("无权限");

        messageService.saveUser(sessionId, question);
        AiAgentService.Answer ans = agent.ask(question, "h5", acc.getId(), acc.getEmpId());
        messageService.saveAssistant(sessionId, ans.getContent(), ans.getSources());

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

    /** 删除会话 */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        H5Account acc = currentAccount(request);
        if (acc == null) return Result.fail("请先登录");
        if (!sessionService.belongsTo(id, acc.getId(), "h5")) return Result.fail("无权限");
        messageService.deleteBySession(id);
        sessionService.delete(id);
        return Result.ok();
    }

    /** 导出会话 Markdown */
    @GetMapping("/session/{id}/export")
    public Result<Map<String, Object>> export(@PathVariable Long id, HttpServletRequest request) {
        H5Account acc = currentAccount(request);
        if (acc == null) return Result.fail("请先登录");
        if (!sessionService.belongsTo(id, acc.getId(), "h5")) return Result.fail("无权限");

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
