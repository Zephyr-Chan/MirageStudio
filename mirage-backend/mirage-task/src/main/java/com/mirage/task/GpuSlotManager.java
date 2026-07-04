package com.mirage.task;

import com.mirage.common.constant.RedisKeyConstants;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GPU 槽位管理器: Lua 原子 DECR/INCR (gpu:slots:available)
 *
 * <p>使用 Lua 脚本保证 "检查 + 扣减" 的原子性, 避免超卖。
 * 槽位占用映射记录在 gpu:slot:holders Hash 中。</p>
 */
@Slf4j
@Component
public class GpuSlotManager {

    private final StringRedisTemplate redisTemplate;

    /**
     * 申请槽位 Lua 脚本:
     * KEYS[1] = gpu:slots:available
     * KEYS[2] = gpu:slot:holders
     * ARGV[1] = taskId
     * ARGV[2] = slotIndex (本次分配的槽位号, 由调用方传入)
     *
     * 返回: 1=成功, 0=槽位不足
     */
    private static final String ACQUIRE_SCRIPT =
            "local available = tonumber(redis.call('GET', KEYS[1]) or '0') " +
            "if available > 0 then " +
            "  redis.call('DECR', KEYS[1]) " +
            "  redis.call('HSET', KEYS[2], ARGV[1], ARGV[2]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * 释放槽位 Lua 脚本:
     * KEYS[1] = gpu:slots:available
     * KEYS[2] = gpu:slot:holders
     * ARGV[1] = taskId
     *
     * 返回: 1=成功, 0=该任务未持有槽位
     */
    private static final String RELEASE_SCRIPT =
            "local held = redis.call('HEXISTS', KEYS[2], ARGV[1]) " +
            "if held == 1 then " +
            "  redis.call('HDEL', KEYS[2], ARGV[1]) " +
            "  redis.call('INCR', KEYS[1]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    private final DefaultRedisScript<Long> acquireScript;
    private final DefaultRedisScript<Long> releaseScript;

    public GpuSlotManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.acquireScript = new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);
        this.releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
    }

    /**
     * 初始化可用槽位数 (服务启动或配置变更时调用)
     */
    public void initSlots(int totalSlots) {
        redisTemplate.opsForValue().set(RedisKeyConstants.GPU_SLOTS_AVAILABLE, String.valueOf(totalSlots));
        log.info("GPU 槽位初始化: total={}", totalSlots);
    }

    /**
     * 获取当前可用槽数
     */
    public long getAvailableSlots() {
        String val = redisTemplate.opsForValue().get(RedisKeyConstants.GPU_SLOTS_AVAILABLE);
        return val == null ? 0L : Long.parseLong(val);
    }

    /**
     * 申请一个 GPU 槽位 (原子操作)
     *
     * @param taskId 任务ID
     * @return true=申请成功
     * @throws BusinessException 槽位不足时抛出
     */
    public boolean acquireSlot(Long taskId) {
        return acquireSlot(String.valueOf(taskId));
    }

    public boolean acquireSlot(String taskId) {
        List<String> keys = List.of(
                RedisKeyConstants.GPU_SLOTS_AVAILABLE,
                RedisKeyConstants.GPU_SLOT_HOLDERS);
        Long result = redisTemplate.execute(acquireScript, keys, taskId, "0");
        boolean ok = result != null && result == 1L;
        if (ok) {
            log.info("GPU 槽位申请成功: taskId={}, 剩余={}", taskId, getAvailableSlots());
        } else {
            log.warn("GPU 槽位不足: taskId={}", taskId);
            throw new BusinessException(BizExceptionEnum.GPU_SLOT_UNAVAILABLE);
        }
        return ok;
    }

    /**
     * 释放 GPU 槽位 (原子操作)
     *
     * @param taskId 任务ID
     * @return true=释放成功
     */
    public boolean releaseSlot(Long taskId) {
        return releaseSlot(String.valueOf(taskId));
    }

    public boolean releaseSlot(String taskId) {
        List<String> keys = List.of(
                RedisKeyConstants.GPU_SLOTS_AVAILABLE,
                RedisKeyConstants.GPU_SLOT_HOLDERS);
        Long result = redisTemplate.execute(releaseScript, keys, taskId);
        boolean ok = result != null && result == 1L;
        if (ok) {
            log.info("GPU 槽位已释放: taskId={}, 剩余={}", taskId, getAvailableSlots());
        } else {
            log.warn("GPU 槽位释放失败(未持有槽位): taskId={}", taskId);
        }
        return ok;
    }
}
