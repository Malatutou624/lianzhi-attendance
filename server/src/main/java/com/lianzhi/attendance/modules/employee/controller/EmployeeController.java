package com.lianzhi.attendance.modules.employee.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 员工管理接口（管理端）
 * GET    /employee/page?current=1&size=10&name=张三&venueId=1
 * POST   /employee        新增
 * PUT    /employee        修改
 * DELETE /employee/{id}   删除
 */
@RestController
@RequestMapping("/employee")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @GetMapping("/page")
    public Result<Page<Employee>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) Long venueId) {
        Page<Employee> page = employeeService.page(new Page<>(current, size),
                Wrappers.<Employee>lambdaQuery()
                        .like(StringUtils.isNotBlank(name), Employee::getName, name)
                        .eq(venueId != null, Employee::getVenueId, venueId)
                        .orderByDesc(Employee::getId));
        return Result.ok(page);
    }

    @PostMapping
    public Result<Void> add(@RequestBody Employee employee) {
        // 工号唯一校验
        long count = employeeService.count(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getEmpNo, employee.getEmpNo()));
        if (count > 0) {
            return Result.fail("工号已存在：" + employee.getEmpNo());
        }
        employeeService.save(employee);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Employee employee) {
        employeeService.updateById(employee);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.removeById(id);
        return Result.ok();
    }
}
