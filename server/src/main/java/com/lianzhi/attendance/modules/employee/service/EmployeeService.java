package com.lianzhi.attendance.modules.employee.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.mapper.CheckinRecordMapper;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.mapper.EmployeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService extends ServiceImpl<EmployeeMapper, Employee> {

    @Autowired
    private CheckinRecordMapper checkinRecordMapper;

    /**
     * 删除员工，同时删除其所有打卡记录（级联删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByIdWithRecords(Long id) {
        checkinRecordMapper.delete(
                Wrappers.<CheckinRecord>lambdaQuery().eq(CheckinRecord::getEmpId, id));
        return removeById(id);
    }
}
