package com.lianzhi.attendance.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.modules.ai.entity.AiSession;
import com.lianzhi.attendance.modules.ai.mapper.AiSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiSessionService {

    @Autowired
    AiSessionMapper sessionMapper;

    public AiSession create(Long userId, String userType) {
        AiSession s = new AiSession();
        s.setUserId(userId);
        s.setUserType(userType);
        s.setTitle("新对话");
        sessionMapper.insert(s);
        return s;
    }

    public List<AiSession> list(Long userId, String userType) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .eq(AiSession::getUserType, userType)
                .orderByDesc(AiSession::getUpdateTime));
    }

    public AiSession get(Long id) {
        return sessionMapper.selectById(id);
    }

    public void rename(Long id, String title) {
        AiSession s = new AiSession();
        s.setId(id);
        s.setTitle(title);
        sessionMapper.updateById(s);
    }

    public void delete(Long id) {
        sessionMapper.deleteById(id);
    }

    /** 判断会话是否属于该用户 */
    public boolean belongsTo(Long sessionId, Long userId, String userType) {
        AiSession s = sessionMapper.selectById(sessionId);
        return s != null && s.getUserId().equals(userId) && s.getUserType().equals(userType);
    }
}
