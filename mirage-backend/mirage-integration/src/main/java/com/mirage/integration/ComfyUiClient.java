package com.mirage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * ComfyUI 客户端: HTTP 调用 ComfyUI API + WebSocket 监听进度
 *
 * <p>HTTP 接口:
 * <ul>
 *   <li>POST /prompt        提交工作流</li>
 *   <li>POST /upload/image  上传输入图片</li>
 *   <li>GET  /history/{id}  查询执行历史</li>
 *   <li>GET  /view          查看输出图片</li>
 *   <li>POST /interrupt     中断当前执行</li>
 * </ul>
 * WebSocket: ws://{host}/ws 监听进度消息 (progress / executing / executed)</p>
 */
@Slf4j
@Component
public class ComfyUiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Getter
    @Value("${comfyui.base-url:http://localhost:8188}")
    private String baseUrl;

    @Getter
    @Value("${comfyui.ws-url:ws://localhost:8188/ws}")
    private String wsUrl;

    /** prompt_id -> 进度回调 */
    private final Map<String, BiConsumer<String, JsonNode>> progressListeners = new ConcurrentHashMap<>();

    public ComfyUiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 提交工作流到 /prompt
     *
     * @param promptJson ComfyUI API 格式工作流 JSON
     * @param clientId   客户端ID (用于 WebSocket 关联)
     * @return ComfyUI 返回的 prompt_id
     */
    public String queuePrompt(String promptJson, String clientId) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("prompt", objectMapper.readTree(promptJson));
            body.put("client_id", clientId);
            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/prompt"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                        "ComfyUI /prompt 失败: HTTP " + response.statusCode() + ", body=" + response.body());
            }
            JsonNode node = objectMapper.readTree(response.body());
            String promptId = node.path("prompt_id").asText();
            log.info("ComfyUI 工作流已提交: promptId={}", promptId);
            return promptId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComfyUI /prompt 调用异常", e);
            throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                    "ComfyUI /prompt 异常: " + e.getMessage());
        }
    }

    /**
     * 上传图片到 ComfyUI (用于 ControlNet / 图生图输入)
     *
     * @param imageBytes 图片字节
     * @param fileName   文件名
     * @param overwrite  是否覆盖同名
     * @return ComfyUI 返回的上传结果 (含 name, subfolder, type)
     */
    public JsonNode uploadImage(byte[] imageBytes, String fileName, boolean overwrite) {
        try {
            String boundary = "----MirageBoundary" + System.currentTimeMillis();
            String multipartBody = buildMultipartBody(boundary, imageBytes, fileName, overwrite);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/upload/image"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(multipartBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                        "ComfyUI /upload/image 失败: HTTP " + response.statusCode());
            }
            JsonNode node = objectMapper.readTree(response.body());
            log.info("ComfyUI 图片上传成功: fileName={}, result={}", fileName, node);
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComfyUI /upload/image 调用异常", e);
            throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                    "ComfyUI 上传图片异常: " + e.getMessage());
        }
    }

    /**
     * 查询执行历史 /history/{prompt_id}
     */
    public JsonNode getHistory(String promptId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/history/" + promptId))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                        "ComfyUI /history 失败: HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComfyUI /history 调用异常: promptId={}", promptId, e);
            throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                    "ComfyUI /history 异常: " + e.getMessage());
        }
    }

    /**
     * 查看/下载输出文件 /view?filename=...&subfolder=...&type=...
     *
     * @return 文件字节
     */
    public byte[] viewFile(String filename, String subfolder, String type) {
        try {
            StringBuilder url = new StringBuilder(baseUrl + "/view?");
            url.append("filename=").append(java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8));
            if (subfolder != null) {
                url.append("&subfolder=").append(java.net.URLEncoder.encode(subfolder, StandardCharsets.UTF_8));
            }
            if (type != null) {
                url.append("&type=").append(java.net.URLEncoder.encode(type, StandardCharsets.UTF_8));
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                        "ComfyUI /view 失败: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComfyUI /view 调用异常: filename={}", filename, e);
            throw new BusinessException(BizExceptionEnum.COMFYUI_CALL_FAIL,
                    "ComfyUI /view 异常: " + e.getMessage());
        }
    }

    /**
     * 中断当前执行 /interrupt
     */
    public void interrupt() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/interrupt"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException(BizExceptionEnum.COMFYUI_INTERRUPT_FAIL,
                        "ComfyUI /interrupt 失败: HTTP " + response.statusCode());
            }
            log.info("ComfyUI 执行已中断");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComfyUI /interrupt 调用异常", e);
            throw new BusinessException(BizExceptionEnum.COMFYUI_INTERRUPT_FAIL,
                    "ComfyUI /interrupt 异常: " + e.getMessage());
        }
    }

    /**
     * 注册进度监听器 (prompt_id -> callback)
     *
     * @param promptId ComfyUI prompt_id
     * @param listener 回调: (messageType, payload)
     */
    public void registerProgressListener(String promptId, BiConsumer<String, JsonNode> listener) {
        progressListeners.put(promptId, listener);
    }

    /**
     * 移除进度监听器
     */
    public void removeProgressListener(String promptId) {
        progressListeners.remove(promptId);
    }

    /**
     * 启动 WebSocket 监听 ComfyUI 进度消息
     * (由调用方在线程池中触发, 收到消息后根据 prompt_id 分发给对应 listener)
     *
     * <p>ComfyUI WebSocket 消息类型:
     * <ul>
     *   <li>progress: { value, max, prompt_id }</li>
     *   <li>executing: { node, prompt_id } (node=null 表示执行完成)</li>
     *   <li>executed: { node, output, prompt_id }</li>
     *   <li>execution_error: { ... prompt_id }</li>
     * </ul></p>
     */
    public void startWebSocketListener() {
        java.net.http.WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new ComfyWebSocketListener())
                .join();
        log.info("ComfyUI WebSocket 监听已启动: {}", wsUrl);
    }

    /**
     * 处理收到的 WebSocket 文本消息, 分发给对应 prompt_id 的监听器
     */
    private void handleMessage(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            String type = node.path("type").asText();
            JsonNode data = node.path("data");
            String promptId = data.path("prompt_id").asText();
            BiConsumer<String, JsonNode> listener = progressListeners.get(promptId);
            if (listener != null) {
                listener.accept(type, data);
            }
        } catch (Exception e) {
            log.warn("解析 ComfyUI WebSocket 消息失败: {}", text, e);
        }
    }

    /**
     * WebSocket 监听器实现
     */
    private class ComfyWebSocketListener implements java.net.http.WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            log.info("ComfyUI WebSocket 连接已建立");
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket webSocket,
                                                              CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            log.error("ComfyUI WebSocket 错误", error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket webSocket,
                                                               int statusCode, String reason) {
            log.warn("ComfyUI WebSocket 关闭: code={}, reason={}", statusCode, reason);
            return null;
        }
    }

    /**
     * 构建 multipart/form-data 请求体 (简化版, 用于上传图片)
     */
    private String buildMultipartBody(String boundary, byte[] imageBytes, String fileName, boolean overwrite) {
        StringBuilder sb = new StringBuilder();
        // overwrite 字段
        sb.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"overwrite\"\r\n\r\n")
                .append(overwrite ? "true" : "false").append("\r\n");
        // image 字段 (注: 此处以 Base64 文本形式发送, 适用于小型图片;
        //  生产环境建议用 HttpClient 的 ofByteArrays Publisher 发送二进制 multipart)
        sb.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"image\"; filename=\"")
                .append(fileName).append("\"\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n")
                .append(java.util.Base64.getEncoder().encodeToString(imageBytes))
                .append("\r\n");
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }
}
