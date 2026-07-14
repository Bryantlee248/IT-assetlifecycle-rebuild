package com.itam.asset.domain;

import com.itam.asset.constants.HotspotColumn;
import com.itam.asset.entity.Asset;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资产字段映射服务：物理列 ↔ attributes(JSONB) 拆分与反向拼装。
 *
 * 拆分（写入）：输入为「字段编码(snake_case) → 值」的合并 Map（由请求体转换而来），
 * 命中 HotspotColumn 白名单的编码路由到物理列，其余进入 attributes。
 * 拼装（读取）：将物理列值与 attributes 合并为统一 Map，供响应/校验使用。
 */
@Service
public class AssetFieldMappingService {

    /**
     * 拆分结果：physical=物理列值集合；attributes=JSONB 扩展字段集合。
     */
    public record SplitResult(Map<String, Object> physical, Map<String, Object> attributes) {
    }

    public SplitResult split(Map<String, Object> values) {
        Map<String, Object> physical = new LinkedHashMap<>();
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                if (HotspotColumn.contains(e.getKey())) {
                    physical.put(e.getKey(), e.getValue());
                } else {
                    attributes.put(e.getKey(), e.getValue());
                }
            }
        }
        return new SplitResult(physical, attributes);
    }

    /**
     * 反向拼装：物理列 + attributes 合并为统一 Map（按字段编码）。
     */
    public Map<String, Object> assemble(Asset asset) {
        Map<String, Object> all = new LinkedHashMap<>(asset.getAttributes());
        for (String col : HotspotColumn.ALL) {
            Object v = asset.getPhysicalValue(col);
            if (v != null) {
                all.put(col, v);
            }
        }
        return all;
    }
}
