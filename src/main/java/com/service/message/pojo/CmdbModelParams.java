package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/21 15:38
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmdbModelParams {

    /**
     * 页码（0开始）
     */
    private Integer pageNum = 0;

    /**
     * 每页条数（默认1000）
     */
    private Integer pageSize = 1000;

    /**
     * 是否需要返回总条数（true=需要）
     */
    private Boolean needCount = true;

    /**
     * 查询条件列表
     */
    private List<QueryCondition> conditions;

    /**
     * 需返回的字段列表（指定返回id、classCode等核心字段）
     */
    private List<String> requiredFields;

    /**
     * 内部静态类：查询条件子实体
     */
    @Data
    public static class QueryCondition {

        /**
         * 查询字段名（如classCode）
         */
        private String field;

        /**
         * 操作（默认为EQ, 可添加其他值如IN）
         */
        private String operator = "EQ";

        /**
         * 查询字段值（如assets_reserve）
         */
        private Object value;
    }
}
