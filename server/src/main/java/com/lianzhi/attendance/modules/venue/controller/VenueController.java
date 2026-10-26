package com.lianzhi.attendance.modules.venue.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 办公场所管理接口
 * GET    /venue/page?current=1&size=10&name=北京   分页查询
 * GET    /venue/list                               全部场所（下拉框用）
 * POST   /venue                                    新增
 * PUT    /venue                                    修改
 * DELETE /venue/{id}                               删除
 */
@RestController
@RequestMapping("/venue")
public class VenueController {

    @Autowired
    private VenueService venueService;

    @GetMapping("/page")
    public Result<Page<Venue>> page(@RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String name) {
        Page<Venue> page = venueService.page(new Page<>(current, size),
                Wrappers.<Venue>lambdaQuery()
                        .like(StringUtils.isNotBlank(name), Venue::getName, name)
                        .orderByDesc(Venue::getId));
        return Result.ok(page);
    }

    @GetMapping("/list")
    public Result<List<Venue>> list() {
        return Result.ok(venueService.list(
                Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1)));
    }

    @PostMapping
    public Result<Void> add(@RequestBody Venue venue) {
        venueService.save(venue);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Venue venue) {
        venueService.updateById(venue);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        venueService.removeById(id);
        return Result.ok();
    }
}
