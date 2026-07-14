package com.itam.metadata.dto;

import java.util.List;
import java.util.UUID;

/**
 * 位置树节点。
 */
public record LocationNode(
        UUID id,
        String name,
        String code,
        String path,
        int sortOrder,
        List<LocationNode> children) {
}
