package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2026/1/29 17:31
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonResponse {

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应提示信息
     */
    private String msg;

    /**
     * 响应数据
     */
    private Object data;

    // ========== 静态快捷方法，简化接口返回写法 ==========
    /**
     * 成功返回（无业务数据）
     */
    public static CommonResponse success() {
        return new CommonResponse(200, "操作成功", null);
    }

    /**
     * 成功返回（带业务数据）
     */
    public static  CommonResponse success(Object data) {
        return new CommonResponse(200, "操作成功", data);
    }

    /**
     * 成功返回（自定义提示语+业务数据）
     */
    public static  CommonResponse success(String msg, Object data) {
        return new CommonResponse(200, msg, data);
    }

    /**
     * 失败返回（自定义状态码+错误信息）
     */
    public static  CommonResponse fail(Integer code, String msg) {
        return new CommonResponse(code, msg, null);
    }

    /**
     * 失败返回（默认500状态码+错误信息）
     */
    public static  CommonResponse fail(String msg) {
        return new CommonResponse(500, msg, null);
    }
}
