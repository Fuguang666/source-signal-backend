package com.sourcesignal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sourcesignal.entity.UserLead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户-线索关联 Mapper
 */
@Mapper
public interface UserLeadMapper extends BaseMapper<UserLead> {

    /** 统计用户已读线索数 */
    @Select("SELECT COUNT(*) FROM user_lead WHERE user_id = #{userId} AND is_read = 1")
    long countReadByUserId(Long userId);
}
