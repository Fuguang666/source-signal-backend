package com.sourcesignal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sourcesignal.entity.UserSubscriptionConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户订阅配置 Mapper
 */
@Mapper
public interface UserSubscriptionConfigMapper extends BaseMapper<UserSubscriptionConfig> {
}
