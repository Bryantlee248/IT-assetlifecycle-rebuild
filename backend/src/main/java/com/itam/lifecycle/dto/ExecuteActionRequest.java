package com.itam.lifecycle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E4 请求体：执行生命周期动作。
 * reason 后端不强制非空（前端弹窗要求填写）；formData / attachmentIds 可空。
 *
 * 使用 record 以获得稳定的 Jackson 反序列化（无 lombok 生成器干扰），
 * 构造器签名保持 (String, Map, List&lt;UUID&gt;)，与既有调用点兼容。
 */
public record ExecuteActionRequest(
        @JsonProperty("reason") String reason,
        @JsonProperty("formData") Map<String, Object> formData,
        @JsonProperty("attachmentIds") List<UUID> attachmentIds
) {
}
