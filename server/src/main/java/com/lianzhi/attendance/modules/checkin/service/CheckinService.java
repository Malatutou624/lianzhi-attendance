package com.lianzhi.attendance.modules.checkin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.mapper.CheckinRecordMapper;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CheckinService extends ServiceImpl<CheckinRecordMapper, CheckinRecord> {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private VenueService venueService;

    /**
     * 分页查询后，把员工姓名和场所名称填充进去（给前端表格展示）
     */
    public void fillNames(Page<CheckinRecord> page) {
        List<CheckinRecord> records = page.getRecords();
        if (records.isEmpty()) {
            return;
        }
        // 一次性查出所有相关员工和场所，避免循环里查库（N+1问题）
        List<Long> empIds = records.stream().map(CheckinRecord::getEmpId).distinct().collect(Collectors.toList());
        List<Long> venueIds = records.stream().map(CheckinRecord::getVenueId).distinct().collect(Collectors.toList());
        Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        Map<Long, String> venueMap = venueService.listByIds(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
        for (CheckinRecord r : records) {
            Employee emp = empMap.get(r.getEmpId());
            if (emp != null) {
                r.setEmpName(emp.getName());
                r.setDept(emp.getDept());
            }
            r.setVenueName(venueMap.get(r.getVenueId()));
        }
    }
}
