package com.lianzhi.attendance.modules.venue.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.mapper.VenueMapper;
import org.springframework.stereotype.Service;

/**
 * 场所业务层：继承 ServiceImpl 后直接拥有 save/updateById/removeById/page 等方法
 */
@Service
public class VenueService extends ServiceImpl<VenueMapper, Venue> {
}
