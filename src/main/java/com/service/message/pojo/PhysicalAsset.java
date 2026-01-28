package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/28 16:57
 * @Description: 设备资产实体对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhysicalAsset {

    /**
     * 资产唯一ID
     */
    private String id;

    /**
     * 资产分类编码（固定为physical_assets）
     */
    private String classCode;

    /**
     * 资产名称（如：TFWQ-7788-0001-sn1107）
     */
    private String name;

    /**
     * 资产序列号（SN码）
     */
    private String sn;

    /**
     * 预留借用到期日期（如：2025-11-12 17:04:08）
     */
    private String reserv_borrow_expiry_date;

    /**
     * 服务器机型名称（如：910B GPU Server(8462Y+)）
     */
    private String host_type;

    /**
     * 资产状态（如：rackInstalling-上架中）
     */
    private String asset_status;

    /**
     * 主设备类型（如：gpuServer-GPU服务器）
     */
    private String main_device_type;

    /**
     * 厂商信息（嵌套对象）
     */
    private BaseObject Manufacturer;

    /**
     * 厂商型号信息（嵌套对象）
     */
    private BaseObject ManufactModel;

    /**
     * 资产分类（如：CateIAssets）
     */
    private String asset_category;

    /**
     * 所属主体（如：st_asset）
     */
    private String ownership_entity;

    /**
     * 预留人信息列表（支持多人预留，数组结构）
     */
    private List<ReservedUser> reserved_user;

    /**
     * 预留关联信息（关联预留单）
     */
    private BaseObject reserve_ref;

    /**
     * 预留变更
     */
    private String reserved_chg;

}
