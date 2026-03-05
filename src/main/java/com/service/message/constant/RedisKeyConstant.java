package com.service.message.constant;

/**
 * @Author: ninth-sun
 * @Date: 2026/1/28 18:28
 * @Description:
 */
public interface RedisKeyConstant {

    /**
     * 分布式计数器Hash Key：{环境}:workflow:counter:{主流程编号}
     */
    public static final String COUNTER_HASH_KEY = "%s:workflow:counter:%s";

    /**
     * Hash字段：子工单总数量
     */
    public static final String HASH_FIELD_TOTAL = "total";

    /**
     * Hash字段：已完成子工单数量
     */
    public static final String HASH_FIELD_COMPLETED = "completed";

    /**
     * Hash字段：同意的子工单数量
     */
    public static final String HASH_FIELD_AGREE = "agree";

    /**
     * Hash字段：工单票据ID
     */
    public static final String HASH_FIELD_TICKET_ID = "ticketId";

    /**
     * Hash字段：交易ID
     */
    public static final String HASH_FIELD_TRANS_ID = "transId";

    /**
     * Hash字段：推送模式
     */
    public static final String HASH_FIELD_PUSH_MODE = "pushMode";

    /**
     * 分布式锁Key：{环境}:workflow:lock:{主流程编号}
     */
    public static final String LOCK_KEY = "%s:workflow:lock:%s";

    /**
     * 分布式锁过期时间（30s，避免死锁，可根据业务调整）
     */
    public static final long LOCK_EXPIRE_SECOND = 30;

    /**
     * 提交流程幂等标志Key：{环境}:workflow:done:{主流程编号}
     */
    public static final String DONE_FLAG_KEY = "%s:workflow:done:%s";

    /**
     * 幂等标志值（固定1表示已执行提交流程）
     */
    public static final String DONE_FLAG_VALUE = "1";

    /**
     * SAP同步任务：当前待同步的update_on日期（分布式存储）
     */
    public static final String SAP_SYNC_CURRENT_DATE = "%s:sap:sync:current_date";
}
