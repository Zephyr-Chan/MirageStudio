package com.mirage.common.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 雪花ID生成器
 *
 * 结构 (64 bit):
 *  1 bit 符号位 | 41 bit 时间戳(毫秒) | 10 bit 工作机器ID | 12 bit 序列号
 *
 * 单机每毫秒可生成 4096 个ID, 可用约 69 年
 */
@Component
public class SnowflakeId {

    /** 起始时间戳 (2024-01-01 00:00:00 UTC) */
    private static final long EPOCH = LocalDate.of(2024, 1, 1)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli();

    /** 机器ID位数 */
    private static final long WORKER_ID_BITS = 10L;

    /** 最大机器ID */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 序列号位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 机器ID左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 时间戳左移位数 */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 序列号掩码 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 默认机器ID (可通过 -Dsnowflake.worker-id 覆盖) */
    private final long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeId() {
        long wid = 1L;
        String prop = System.getProperty("snowflake.worker-id");
        if (prop != null && !prop.isEmpty()) {
            try {
                wid = Long.parseLong(prop);
            } catch (NumberFormatException ignored) {
            }
        }
        if (wid < 0 || wid > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "worker Id 不能为负且不能超过 " + MAX_WORKER_ID + ", 实际: " + wid);
        }
        this.workerId = wid;
    }

    public SnowflakeId(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "worker Id 不能为负且不能超过 " + MAX_WORKER_ID + ", 实际: " + workerId);
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个ID (线程安全)
     */
    public synchronized long nextId() {
        long timestamp = currentMillis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "时钟回拨, 拒绝生成ID. 当前=" + timestamp + " 上次=" + lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                // 当前毫秒序列号耗尽, 等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成下一个ID (字符串)
     */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = currentMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentMillis();
        }
        return timestamp;
    }

    private long currentMillis() {
        return Instant.now().toEpochMilli();
    }
}
