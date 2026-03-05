package com.service.message.property;

import lombok.Getter;

import java.util.Objects;

/**
 * 消息模板枚举，集中管理所有消息模板
 */
@Getter
public enum MessageTemplate {


    // 通用模板
    COMMON_TEMPLATE("COMMON_TEMPLATE", "【请关注】SceneDIY工单已经分派给您，请审批。"),

    /**
     * 处理中状态消息模板
     */
    RUNNING("RUNNING", "【请关注】SceneDIY工单已经分派给您，请审批。\n提单时间：%s\n单号：%s\n当前环节：%s\n服务名称：%s\n当前处理人：%s"),

    /**
     * 挂起状态消息模板
     */
    SUSPEND("SUSPEND", "【请关注】SceneDIY工单已挂起。\n提单时间：%s\n单号：%s\n当前处理人：%s"),

    /**
     * 撤单状态消息模板
     */
    WITHDRAW("WITHDRAW", "【请关注】SceneDIY工单已撤单。\n提单时间：%s\n单号：%s\n当前处理人：%s"),

    /**
     * 拒绝状态消息模板
     */
    TERMINATION("TERMINATION", "【请关注】SceneDIY工单已经被终止。\n提单时间：%s\n单号：%s\n当前处理人：%s");

    private final String code;

    private final String template;

    MessageTemplate(String code, String template) {
        this.code = code;
        this.template = template;
    }

    public static MessageTemplate getByCode(String code) {
        for (MessageTemplate template : values()) {
            if (Objects.equals(template.code, code)) {
                return template;
            }
        }
        // 未找到时抛出异常
        throw new IllegalArgumentException("未找到code为：" + code + "的消息模板");
    }

    /**
     * 根据参数生成消息模板
     * @param params
     * @return
     */
    public String generateMessageByCode(String... params) {
        return String.format(template, params);
    }
}

