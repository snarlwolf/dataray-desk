package com.skydawn.desk.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.skydawn.desk.core.entity.DialogueRecord;

@Mapper
public interface DialogueRecordMapper {
    DialogueRecord findById(String id);
    List<DialogueRecord> findBySessionId(String sessionId);
    int insert(DialogueRecord record);
    int delete(String id);
}
