package com.lianzhi.attendance.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianzhi.attendance.modules.ai.entity.AiMessage;
import com.lianzhi.attendance.modules.ai.mapper.AiMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiMessageService {

    @Autowired
    AiMessageMapper messageMapper;

    public AiMessage saveUser(Long sessionId, String content) {
        AiMessage m = new AiMessage();
        m.setSessionId(sessionId);
        m.setRole("user");
        m.setContent(content);
        messageMapper.insert(m);
        return m;
    }

    public AiMessage saveAssistant(Long sessionId, String content, String sources) {
        AiMessage m = new AiMessage();
        m.setSessionId(sessionId);
        m.setRole("assistant");
        m.setContent(content);
        m.setSources(sources);
        messageMapper.insert(m);
        return m;
    }

    /** 取最近 n 条（不含最新 user，供上下文压缩用） */
    public List<AiMessage> recent(Long sessionId, int n) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByDesc(AiMessage::getCreateTime)
                .last("LIMIT " + n));
    }

    /** 取会话全部消息（按时间升序） */
    public List<AiMessage> all(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime));
    }

    public void deleteBySession(Long sessionId) {
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId));
    }
}
