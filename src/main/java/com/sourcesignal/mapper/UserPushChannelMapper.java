package com.sourcesignal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sourcesignal.entity.UserPushChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户推送渠道配置 Mapper
 */
@Mapper
public interface UserPushChannelMapper extends BaseMapper<UserPushChannel> {
}
