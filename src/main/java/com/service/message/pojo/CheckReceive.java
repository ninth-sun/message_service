package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/7 10:22
 * @Description: 采购验收单推送实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckReceive {

    /**
     * 采购验收单号（如：CGYS-202511-0003）
     */
    private String workFlowNumber;

    /**
     * po单号（如：4500003565）
     */
    private String purchaseOrderNumber;

    /**
     * po行号（如：10）
     */
    private String orderLineNumber;

    /**
     * 固定资产编号（如：CN3616000229）
     */
    private String fixedAssetNumber;

    /**
     * 序列号（如：SN3616000229）
     */
    private String serialNumber;

    /**
     * 保管人（如：gaocen）
     */
    private String custodian;

    /**
     * GPU卡型号及数量（如：GPU卡型号及数量）
     */
    private String gpuModelAndCount;

}
