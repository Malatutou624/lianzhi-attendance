package com.lianzhi.attendance.modules.employee.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService extends ServiceImpl<EmployeeMapper, Employee> {
}
