package com.mirage.web.controller;

import com.mirage.common.R;
import com.mirage.common.dto.TaskStatusDTO;
import com.mirage.task.TaskStatusStore;
import com.mirage.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务控制器: 查询任务实时状态
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskStatusStore taskStatusStore;

    /**
     * 查询任务实时状态 (从 Redis 读取)
     */
    @GetMapping("/{id}")
    public R<TaskStatusDTO> getStatus(@PathVariable String id) {
        UserContext.requireUserId();
        TaskStatusDTO dto = taskStatusStore.get(id);
        if (dto == null) {
            return R.ok(TaskStatusDTO.builder()
                    .taskId(id)
                    .status("UNKNOWN")
                    .progress(0)
                    .message("任务状态不存在或已过期")
                    .build());
        }
        return R.ok(dto);
    }
}
