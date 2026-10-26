package com.lianzhi.attendance.modules.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
