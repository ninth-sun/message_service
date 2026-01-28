package com.service.message.pojo;

import lombok.Data;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/9/18 14:18
 * @Description:
 */
@Data
public class ArrivalPlan {

    /**
     *编号
     */
    private String name;

    /**
     *PR单号
     */
    private String pr_no;

    /**
     *PO单号
     */
    private String po_no;

    /**
     *PO行号
     */
    private String po_line_no;

    /**
     *产品需求名称
     */
    private String product;

    /**
     *供应商
     */
    private String supplier;

    /**
     *厂商
     */
    private String vendor;

    /**
     *规格型号
     */
    private String model;

    /**
     *采购BP
     */
    private List<Object> procurement_business_partner;

    private String procurement_business_partner_str;

    /**
     *采购数量
     */
    private String procurement_amount;

    /**
     *批次号
     */
    private String batch_no;

    /**
     *预计到货数量
     */
    private String expect_arrival_amount;

    /**
     * 预计到货时间
     */
    private String expect_arrival_date;

    /**
     * 是否包含上架时间
     */
    private String include_rack_install;

    /**
     * 资源code
     */
    private String classCode;

    /**
     * OA备货单号
     */
    private String oa_stock_order_no;
    /**
     * 物料号
     */
    private String material_no;
    /**
     * 机型编码
     */
    private String host_type;
    /**
     * 库存地点
     */
    private String location;
    /**
     * 公司代码
     */
    private String company_code;

    /**
     *计划BP
     */
    private List<Object> plan_procurement_business_partner;

    private String plan_procurement_business_partner_str;

}
