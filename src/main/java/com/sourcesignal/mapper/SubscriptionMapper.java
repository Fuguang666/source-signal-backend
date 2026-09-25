package com.sourcesignal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sourcesignal.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订阅 Mapper
 */
@Mapper
public interface SubscriptionMapper extends BaseMapper<Subscription> {
}
