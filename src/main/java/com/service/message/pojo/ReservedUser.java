package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/1 16:59
 * @Description: 预留人信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservedUser {

    /**
     * 用户唯一标识（如：00000000000000163687500087829476）
     */
    private String uid;

    /**
     * 用户姓名（如：王添）
     */
    private String name;

    /**
     * 用户账号（如：wangtian2.vendor）
     */
    private String account;
}
