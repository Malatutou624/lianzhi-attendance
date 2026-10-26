package com.lianzhi.attendance.modules.rule.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.mapper.AttendRuleMapper;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考勤规则业务层
 */
@Service
public class RuleService extends ServiceImpl<AttendRuleMapper, AttendRule> {

    @Autowired
    private VenueService venueService;

    /**
     * 把场所名称填充到规则对象里（规则表只存了 venueId，前端要显示名字）
     */
    public void fillVenueNames(List<AttendRule> list) {
        if (list.isEmpty()) {
            return;
        }
        // 一次性查出所有相关场所，避免循环查库
        List<Long> venueIds = list.stream().map(AttendRule::getVenueId).distinct().collect(Collectors.toList());
        Map<Long, String> venueMap = venueService.listByIds(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
        for (AttendRule rule : list) {
            rule.setVenueName(venueMap.get(rule.getVenueId()));
        }
    }

    /**
     * 取某个场所的考勤规则；没配置就返回默认规则（9点上班、18点下班、宽限10分钟）
     * 打卡判定时调用，保证就算管理员忘了配规则，打卡也能正常用
     */
    public AttendRule getByVenueId(Long venueId) {
        AttendRule rule = getOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AttendRule>lambdaQuery()
                        .eq(AttendRule::getVenueId, venueId));
        if (rule == null) {
            rule = new AttendRule();
            rule.setVenueId(venueId);
            rule.setWorkStart(java.time.LocalTime.of(9, 0));
            rule.setWorkEnd(java.time.LocalTime.of(18, 0));
            rule.setLateMinutes(10);
        }
        return rule;
    }
}
