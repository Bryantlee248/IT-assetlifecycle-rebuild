package com.itam.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 审批决策请求体。approve 的 comment 可选；reject 的 comment 必填（服务端校验）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequest {

    private String comment;
}
