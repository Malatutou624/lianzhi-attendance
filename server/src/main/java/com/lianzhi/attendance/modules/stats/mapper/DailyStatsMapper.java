package com.lianzhi.attendance.modules.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianzhi.attendance.modules.stats.entity.DailyStats;
import org.apache.ibatis.annotations.Mapper;

/** daily_stats 表数据库操作接口 */
@Mapper
public interface DailyStatsMapper extends BaseMapper<DailyStats> {
}
