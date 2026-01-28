package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/28 17:00
 * @Description: 配件资产实体对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessoryAsset {

    /**
     * 资产唯一ID
     */
    private String id;

    /**
     * 资产分类编码（固定为accessory_asset）
     */
    private String classCode;

    /**
     * 资产序列号（SN码）
     */
    private String sn;

    /**
     * 资产名称
     */
    private String name;

    /**
     * 配件类型（如：光模块）
     */
    private String accessory_type;

    /**
     * 厂商信息（嵌套对象）
     */
    private BaseObject Manufacturer;

    /**
     * 厂商型号（嵌套对象）
     */
    private BaseObject ManufactModel;

    /**
     * 资产分类
     */
    private String asset_category;

    /**
     * 所属主体
     */
    private String ownership_entity;

    /**
     * 资产状态（如：rackInstalling-上架中）
     */
    private String asset_status;

    /**
     * 预留借用到期日期（如：2025-11-12 17:04:08）
     */
    private String reserv_borrow_expiry_date;

    /**
     * 预留人信息列表（支持多人预留，数组结构）
     */
    private List<ReservedUser> reserved_user;

    /**
     * 预留关联信息（嵌套对象）
     */
    private BaseObject reserve_ref;

    /**
     * 预留变更
     */
    private String reserved_chg;

}
