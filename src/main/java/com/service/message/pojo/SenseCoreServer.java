package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2026/4/1 10:49
 * @Description: SenseCore服务器
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SenseCoreServer {

    /**
     * 资产唯一ID
     */
    private String id;

    /**
     * 资产分类编码
     */
    private String classCode;

    /**
     * 资产序列号（SN码）
     */
    private String name;

    /**
     * 资产编号
     */
    private String asset_code;

    /**
     * 设备类型（如：gpuServer-GPU服务器）
     */
    private String main_device_type;

    /**
     * U数
     */
    private String u_number;

    /**
     * 厂商信息（嵌套对象）
     */
    private BaseObject Manufacturer;

    /**
     * 厂商型号信息（嵌套对象）
     */
    private BaseObject ManufactModel;

    /**
     * 服务器机型名称（如：910B GPU Server(8462Y+)）
     */
    private String host_type;

    /**
     * mc
     */
    private BaseObject machine_code;

    /**
     * 所在库位
     */
    private BaseObject wh_location;

    /**
     * 维保结束时间（如：2025-11-12 17:04:08）
     */
    private String maintenance_end_time;

}
