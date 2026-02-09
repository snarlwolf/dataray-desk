package com.skydawn.desk.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.skydawn.desk.core.entity.Conversation;

@Mapper
public interface ConversationMapper {

    Conversation findById(Long id);

    List<Conversation> findByUserId(Long userId);

    int insert(Conversation conversation);

    int update(Conversation conversation);

    int deleteById(Long id);
}
