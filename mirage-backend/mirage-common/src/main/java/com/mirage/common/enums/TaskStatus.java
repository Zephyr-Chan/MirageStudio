package com.mirage.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 任务状态机
 * PENDING -> QUEUED -> RUNNING -> SUCCESS / FAILED / CANCELLED
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    PENDING("待处理"),
    QUEUED("已入队"),
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    CANCELLED("已取消");

    private final String desc;

    /**
     * 判断是否可流转到目标状态
     */
    public boolean canTransitTo(TaskStatus target) {
        Set<TaskStatus> allowed = switch (this) {
            case PENDING -> EnumSet.of(QUEUED, CANCELLED, FAILED);
            case QUEUED -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case RUNNING -> EnumSet.of(SUCCESS, FAILED, CANCELLED);
            case SUCCESS, FAILED, CANCELLED -> EnumSet.noneOf(TaskStatus.class);
        };
        return allowed.contains(target);
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
