package com.sourcesignal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sourcesignal.entity.PushRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 推送记录 Mapper
 */
@Mapper
public interface PushRecordMapper extends BaseMapper<PushRecord> {

    /** 按用户ID和渠道分页查询（按推送时间倒序） */
    @Select("SELECT * FROM push_record WHERE user_id = #{userId} AND channel = #{channel} ORDER BY pushed_at DESC")
    IPage<PushRecord> selectPageByUserAndChannel(IPage<PushRecord> page,
                                                    @Param("userId") Long userId,
                                                    @Param("channel") String channel);

    /** 查询用户指定渠道的所有推送记录 */
    @Select("SELECT * FROM push_record WHERE user_id = #{userId} AND channel = #{channel} ORDER BY pushed_at DESC")
    List<PushRecord> selectByUserAndChannel(@Param("userId") Long userId,
                                              @Param("channel") String channel);

    /** 统计用户指定渠道的推送数 */
    @Select("SELECT COUNT(*) FROM push_record WHERE user_id = #{userId} AND channel = #{channel}")
    long countByUserAndChannel(@Param("userId") Long userId,
                                @Param("channel") String channel);
}
