package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/21 17:10
 * @Description: 资产预留需求表（耗材）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumablesReserve {

    /**
     * 耗材预留记录唯一ID（如：691d38b5471291305b790e58）
     */
    private String id;

    /**
     * 耗材分类编码（固定为 consumables_reserve）
     */
    private String classCode;

    /**
     * 耗材预留名称（如：公有云备品备件预留/网线/超五类网线-5M/ 00.A3-WH501.SHLGS1）
     */
    private String name;

    /**
     * 耗材编码信息（嵌套对象）
     */
    private BaseObject consumable_code;

    /**
     * 资产用途（如：公有云备品备件预留）
     */
    private String asset_purpose;

    /**
     * 需求数量
     */
    private Integer demando_count;

    /**
     * 预留人信息列表（支持多人预留，数组结构）
     */
    private List<ReservedUser> reserved_user;

    /**
     * 预留借用到期日期（如：2025-11-19 00:00:00）
     */
    private String reserv_borrow_expiry_date;

    /**
     * 预留数量（与demando_count可能一致，按实际业务区分）
     */
    private Integer reserved_num;

    /**
     * 预留变更
     */
    private String reserved_chg;

    /**
     * (为发起备品备件补充数量流程时使用)预留人（如：张三）
     */
    private String applicant;
}
