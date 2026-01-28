package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/21 16:15
 * @Description: 资产预留需求表（设备、配件）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetReserve {

    /**
     * 资产预留记录唯一ID（如：6911aa88c898587dadb86978）
     */
    private String id;

    /**
     * 资产分类编码（固定为 assets_reserve）
     */
    private String classCode;

    /**
     * 资产用途（如：金山云）
     */
    private String asset_purpose;

    /**
     * 资产名称（如：金山云/配件、金山云/设备）
     */
    private String name;

    /**
     * 资产类型（如：配件、设备）
     */
    private String asset_type;

    /**
     * 需求数量（预留的资产数量）
     */
    private Integer demando_count;
}
