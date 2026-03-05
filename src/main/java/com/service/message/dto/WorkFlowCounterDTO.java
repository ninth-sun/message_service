package com.service.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2026/1/28 18:11
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkFlowCounterDTO {

    /**
     * 主流程编号
     */
    private String mainProcessId;

    /**
     * 子工单数量
     */
    private Integer subOrderNum;

    /**
     * 操作类型：仅支持agree/reject
     */
    private String operation;

    private String ticketId;

    private String transId;

    /**
     * 提交方式：
     * 0:无论子工单结果如何,流程都自动提交
     * 1:所有子工单结果为agree,流程才自动提交
     */
    private String pushMode;
}
