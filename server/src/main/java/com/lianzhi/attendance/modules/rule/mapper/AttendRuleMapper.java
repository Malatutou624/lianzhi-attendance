package com.lianzhi.attendance.modules.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤规则 Mapper：继承 BaseMapper 就自动有了增删改查方法，不用写 SQL
 */
@Mapper
public interface AttendRuleMapper extends BaseMapper<AttendRule> {
}
