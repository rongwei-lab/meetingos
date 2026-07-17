package com.meetingos.publicapi;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 公开查询读取与历史写入必须分成前后两个事务阶段，避免同一请求同时占用两个连接。
 */
@Component
public class PublicQueryTransactionRunner {

    @Transactional(readOnly = true)
    public <T> T read(Supplier<T> action) {
        return action.get();
    }
}
