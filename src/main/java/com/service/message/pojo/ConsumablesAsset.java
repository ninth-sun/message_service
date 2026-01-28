package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/12 17:33
 * @Description: 耗材资产实体对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumablesAsset {

    /**
     * 资产唯一ID
     */
    private String id;

    /**
     * 资产分类编码
     */
    private String classCode;

    /**
     * 库存数量
     */
    private Integer store_num;

    /**
     * 可用数量
     */
    private Integer usage_num;

    /**
     * 预留数量
     */
    private Integer reserved_num;
}
