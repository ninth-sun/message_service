package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/9 14:59
 * @Description: 资产预留需求明细表（设备、配件）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetsReserveDetail {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 分类编码
     */
    private String classCode;

    /**
     * 资产名称
     */
    private String name;

    /**
     * 资产用途
     */
    private String asset_purpose;

    /**
     * 资产类型（如：设备）
     */
    private String asset_type;

    /**
     * 主设备类型（如：gpuServer）
     */
    private String main_device_type;

    /**
     * 配件类型（如：光模块）
     */
    private String accessory_type;

    /**
     * 厂商信息
     */
    private BaseObject Manufacturer;

    /**
     * 厂商型号信息
     */
    private BaseObject manufacturer_model;

    /**
     * 服务器机型名称
     */
    private String host_type;

    /**
     * 需求数量
     */
    private Integer demando_count;

    /**
     * 预留人列表
     */
    private List<ReservedUser> reserved_user;

    /**
     * 关联资产预留信息
     */
    private BaseObject link_assets_reserve;

}
