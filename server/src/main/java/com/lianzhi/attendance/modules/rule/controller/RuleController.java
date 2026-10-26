package com.lianzhi.attendance.modules.rule.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考勤规则接口（管理端）
 * GET  /rule/list   查询所有场所的考勤规则
 * POST /rule/save   保存规则（传了id就修改，没传就新增）
 */
@RestController
@RequestMapping("/rule")
public class RuleController {

    @Autowired
    private RuleService ruleService;

    /** 查询所有规则，并带上场所名称（方便前端表格展示） */
    @GetMapping("/list")
    public Result<List<AttendRule>> list() {
        List<AttendRule> list = ruleService.list();
        ruleService.fillVenueNames(list);
        return Result.ok(list);
    }

    /**
     * 保存规则：前端传 {venueId, workStart, workEnd, lateMinutes}
     * 如果这个场所已经有规则了，就更新；没有就新增。
     * 这样前端只管提交，不用判断是新增还是修改。
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody AttendRule rule) {
        if (rule.getVenueId() == null) {
            return Result.fail("缺少场所ID");
        }
        // 查这个场所是否已有规则
        AttendRule exist = ruleService.getOne(
                Wrappers.<AttendRule>lambdaQuery().eq(AttendRule::getVenueId, rule.getVenueId()));
        if (exist != null) {
            // 已有 → 把新值覆盖到旧记录上再更新
            rule.setId(exist.getId());
            ruleService.updateById(rule);
        } else {
            // 没有 → 直接新增一条
            ruleService.save(rule);
        }
        return Result.ok();
    }
}
