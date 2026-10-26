package com.lianzhi.attendance.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianzhi.attendance.modules.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_user 表的数据库操作接口
 * 继承 BaseMapper 后自动拥有：insert/deleteById/updateById/selectById/selectList 等方法，不用写 SQL
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
