package com.lianzhi.attendance.modules.ai.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.ai.entity.AiMessage;
import com.lianzhi.attendance.modules.ai.entity.AiSession;
import com.lianzhi.attendance.modules.ai.mapper.AiMessageMapper;
import com.lianzhi.attendance.modules.ai.mapper.AiSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 会话自动清理任务
 * 每天凌晨 02:00 清理超过 30 天未访问的 ai_session 和对应 ai_message
 * 防止聊天记录无限增长
 *
 * cron: "0 0 2 * * ?" —— 每天 02:00 执行
 */
@Slf4j
@Component
public class AiSessionCleanTask {

    /** 会话保留天数 */
    private static final int RETAIN_DAYS = 30;

    @Autowired private AiSessionMapper aiSessionMapper;
    @Autowired private AiMessageMapper aiMessageMapper;

    @Scheduled(cron = "0 0 2 * * ?")
    public void clean() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETAIN_DAYS);
        log.info("[AI清理] 开始清理 {} 天前的会话，截止时间 {}", RETAIN_DAYS, cutoff);

        // 1. 找出超过 30 天未访问的 session
        List<AiSession> oldSessions = aiSessionMapper.selectList(
                Wrappers.<AiSession>lambdaQuery().lt(AiSession::getUpdateTime, cutoff));

        if (oldSessions.isEmpty()) {
            log.info("[AI清理] 没有需要清理的会话");
            return;
        }

        List<Long> sessionIds = oldSessions.stream().map(AiSession::getId).toList();

        // 2. 先删 message（外键依赖虽然没加，但先删干净）
        int msgDeleted = aiMessageMapper.delete(
                Wrappers.<AiMessage>lambdaQuery().in(AiMessage::getSessionId, sessionIds));

        // 3. 再删 session
        int sesDeleted = aiSessionMapper.deleteBatchIds(sessionIds);

        log.info("[AI清理] 完成：删除 {} 个 session，{} 条 message", sesDeleted, msgDeleted);
    }
}
