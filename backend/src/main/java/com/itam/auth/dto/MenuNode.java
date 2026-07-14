package com.itam.auth.dto;

import java.util.List;

public record MenuNode(
        String key,
        String title,
        String path,
        String icon,
        List<MenuNode> children
) {
}
