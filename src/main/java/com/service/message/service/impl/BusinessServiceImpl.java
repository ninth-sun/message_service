package com.service.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.service.message.constant.RedisKeyConstant;
import com.service.message.dto.WorkFlowCounterDTO;
import com.service.message.pojo.CmdbModelParams;
import com.service.message.pojo.PhysicalAsset;
import com.service.message.pojo.SenseCoreServer;
import com.service.message.pojo.SenseCoreSwitch;
import com.service.message.property.SceneDiyProperty;
import com.service.message.property.WechatProperty;
import com.service.message.service.BusinessService;
import com.service.message.utils.HttpUtil;
import com.service.message.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: ninth-sun
 * @Date: 2026/1/28 18:08
 * @Description: 业务服务接口实现类
 */
@Service
@Slf4j
public class BusinessServiceImpl implements BusinessService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WechatProperty wechatProperty;

    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    public String getAccessToken() {
        String params = String.format("corpid=%s&corpsecret=%s", wechatProperty.getCorpId(), wechatProperty.getSecret());
        String requestGet = HttpUtil.doRequestGet(wechatProperty.getUrl() + "/gettoken", params);
        String accessToken = "";
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            if (jsonObject != null) {
                accessToken = jsonObject.getString("access_token");
            }
        }
        return accessToken;
    }

    public String getUserIdByEmail(String email, String accessToken) {
        String requestBody = String.format("{\"email\":\"%s\",\"email_type\":2}", email);
        String requestPost = HttpUtil.doRequestPost(wechatProperty.getUrl() + "/user/get_userid_by_email?access_token=" + accessToken, requestBody);
        String userId = null;
        if (StringUtil.isNotEmpty(requestPost)) {
            JSONObject jsonObject = JSON.parseObject(requestPost);
            if (jsonObject != null) {
                userId = jsonObject.getString("userid");
            }
        }
        return userId;
    }

    public void sendCustomMessage(String email, String msgtype, String content) {
        // 获取企微accessToken
        String accessToken = getAccessToken();

        // 发送用户
        String touser = getUserIdByEmail(email, accessToken);

        // 发送消息
        String requestBody = String.format("{\"touser\":\"%s\",\"msgtype\":\"%s\",\"agentid\":%s,\"%s\":%s}",
                touser, msgtype, wechatProperty.getAgentId(), msgtype, content);

        HttpUtil.doRequestPost(wechatProperty.getUrl() + "/message/send?access_token=" + accessToken, requestBody);
    }

    @Override
    public void initCounter(WorkFlowCounterDTO dto) {
        String env = sceneDiyProperty.getEnv();
        String mainProcessId = dto.getMainProcessId();
        int total = dto.getSubOrderNum();
        String ticketId = dto.getTicketId();
        String transId = dto.getTransId();
        String pushMode = dto.getPushMode();

        // 1. 构建Redis Hash Key
        String counterKey = String.format(RedisKeyConstant.COUNTER_HASH_KEY, env, mainProcessId);

        try {
            // 2. 幂等校验：判断计数器是否已初始化，已初始化则直接返回（避免覆盖总数量）
            if (redisTemplate.hasKey(counterKey)) {
                log.warn("【计数器初始化】环境：{}，主流程编号：{}，计数器已存在，无需重复初始化", env, mainProcessId);
                return;
            }

            // 3. 初始化Hash字段：total=子工单数量，completed=0，agree=0
            Map<String, Object> counterMap = new HashMap<>();
            counterMap.put(RedisKeyConstant.HASH_FIELD_TOTAL, String.valueOf(total));
            counterMap.put(RedisKeyConstant.HASH_FIELD_COMPLETED, "0");
            counterMap.put(RedisKeyConstant.HASH_FIELD_AGREE, "0");
            counterMap.put(RedisKeyConstant.HASH_FIELD_TICKET_ID, ticketId);
            counterMap.put(RedisKeyConstant.HASH_FIELD_TRANS_ID, transId);
            counterMap.put(RedisKeyConstant.HASH_FIELD_PUSH_MODE, pushMode);
            redisTemplate.opsForHash().putAll(counterKey, counterMap);

            log.info("【计数器初始化成功】环境：{}，主流程编号：{}，子工单总数量：{}，ticketId：{}，transId：{}",
                    env, mainProcessId, total, ticketId, transId);
        } catch (Exception e) {
            log.error("【计数器初始化失败】环境：{}，主流程编号：{}，ticketId：{}，transId：{}，异常信息：",
                    env, mainProcessId, dto.getTicketId(), dto.getTransId(), e);
            throw new RuntimeException("计数器初始化失败，请检查Redis连接或参数", e);
        }
    }

    @Override
    @Async
    public void updateCounter(WorkFlowCounterDTO dto) {
        String env = sceneDiyProperty.getEnv();
        String mainProcessId = dto.getMainProcessId();
        String operation = dto.getOperation();

        log.info("【计数器更新开始】环境：{}，主流程编号：{}，操作：{}", env, mainProcessId, operation);

        // 1. 构建Redis Key
        String counterKey = String.format(RedisKeyConstant.COUNTER_HASH_KEY, env, mainProcessId);
        String lockKey = String.format(RedisKeyConstant.LOCK_KEY, env, mainProcessId);
        String doneKey = String.format(RedisKeyConstant.DONE_FLAG_KEY, env, mainProcessId);

        try {
            // 前置校验：计数器是否存在
            if (!redisTemplate.hasKey(counterKey)) {
                log.error("【计数器更新】环境：{}，主流程编号：{}，计数器未初始化，无法更新", env, mainProcessId);
                throw new RuntimeException("计数器未初始化，请先调用初始化接口");
            }

            // 2. 原子更新计数器：Redis hincrby是单命令，天然保证分布式原子性
            // 2.1 已完成数量+1
            long completed = redisTemplate.opsForHash().increment(counterKey, RedisKeyConstant.HASH_FIELD_COMPLETED, 1);
            // 2.2 操作是agree则同意数量+1
            long agree = 0;
            if ("agree".equals(operation)) {
                agree = redisTemplate.opsForHash().increment(counterKey, RedisKeyConstant.HASH_FIELD_AGREE, 1);
            }
            // 2.3 获取子工单总数量
            long total = Long.parseLong(redisTemplate.opsForHash().get(counterKey, RedisKeyConstant.HASH_FIELD_TOTAL).toString());

            log.info("【计数器更新成功】环境：{}，主流程编号：{}，操作：{}，总数量：{}，已完成：{}，同意：{}",
                    env, mainProcessId, operation, total, completed, agree);

            // 3. 判断是否所有子工单都完成：已完成数量 == 总数量
            // 未全部完成，直接返回
            if (completed != total) {
                return;
            }

            // 4. 全部完成：触发提交流程（核心：分布式锁+双重幂等校验，保证仅执行一次）
            // 4.1 锁外先查幂等标志：已执行则直接返回（提升性能，避免无效抢锁）
            if (redisTemplate.hasKey(doneKey)) {
                log.warn("【提交流程】环境：{}，主流程编号：{}，已执行过提交流程，无需重复执行", env, mainProcessId);
                return;
            }

            // 4.2 获取分布式锁：SET NX EX + UUID防误删
            // 生成唯一标识，防止误删锁
            String lockValue = UUID.randomUUID().toString();
            boolean isLock = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue,
                    RedisKeyConstant.LOCK_EXPIRE_SECOND, TimeUnit.SECONDS);

            if (!isLock) {
                log.warn("【提交流程】环境：{}，主流程编号：{}，获取分布式锁失败，其他线程正在执行", env, mainProcessId);
                return;
            }

            try {
                // 4.3 锁内二次幂等校验：防止多线程抢锁时，前一个线程已设置标志
                if (redisTemplate.hasKey(doneKey)) {
                    log.warn("【提交流程】环境：{}，主流程编号：{}，锁内检测到已执行提交流程", env, mainProcessId);
                    return;
                }

                String pushMode = redisTemplate.opsForHash().get(counterKey, RedisKeyConstant.HASH_FIELD_PUSH_MODE).toString();

                // 如果同意数量 != 总数量, 并且推送模式不为0, 则不执行提交流程, 邮件、企微通知提单人撤单
                if (agree != total && !"0".equals(pushMode)) {
                    String ticketId = redisTemplate.opsForHash().get(counterKey, RedisKeyConstant.HASH_FIELD_TICKET_ID).toString();
                    // 查询工单详情
                    String ticketUrl = String.format("%s/scenediy/open/ticket/ticket/%s?apikey=%s",
                            sceneDiyProperty.getUrl(), ticketId, sceneDiyProperty.getApiKey());
                    JSONObject ticketObject = JSON.parseObject(HttpUtil.doRequestGet(ticketUrl, null));
                    String email = ticketObject.getJSONObject("data").getJSONObject("profile").getString("email");

                    // 邮件通知提单人撤单
                    Map<String, String> emailParam = new HashMap<>();
                    emailParam.put("receiver", email);
                    String title = "【工单撤单通知】您提交的工单：" + mainProcessId + " 需要进行撤单处理";
                    emailParam.put("subject", title);
                    String description = "您提交的工单 NO2026020319052011 中，存在子工单审批未通过。\n" +
                            "\n" +
                            "当前流程要求：子工单需全部审批通过方可继续主流程。\n" +
                            "由于不满足该条件，主工单需进行撤单处理。\n" +
                            "未通过详情请参看关联子工单。\n" +
                            "\n" +
                            "请您撤销工单并重新提交。";
                    emailParam.put("message", description);
                    String emailUrl = String.format("%s/custom/serviceapi/v1/email/send", sceneDiyProperty.getUrl());
                    HttpUtil.doRequestPost(emailUrl, JSON.toJSONString(emailParam));

                    // 企微通知提单人撤单
                    String message = String.format("{\"title\": \"%s\",\"description\": \"%s\"," +
                            "\"url\": \"https://tenon-scenediy.sensetime.com/redirect.html\",\"btntxt\": \"点击处理\"}", title, description);
                    this.sendCustomMessage(email, "textcard", message);

                    // 通知后，直接返回，不执行提交流程
                    return;
                }

                // 4.4 触发提交流程方法（仅执行一次）
                String ticketId = redisTemplate.opsForHash().get(counterKey, RedisKeyConstant.HASH_FIELD_TICKET_ID).toString();
                String transId = redisTemplate.opsForHash().get(counterKey, RedisKeyConstant.HASH_FIELD_TRANS_ID).toString();
                this.submitProcess(ticketId, transId);

                // 4.5 设置幂等标志：永久存储（后续可根据业务加过期时间）
                redisTemplate.opsForValue().set(doneKey, RedisKeyConstant.DONE_FLAG_VALUE);
                log.info("【提交流程】环境：{}，主流程编号：{}，提交流程执行成功，已设置幂等标志", env, mainProcessId);

            } finally {
                // 释放分布式锁：仅删除自己加的锁（对比lockValue），防止误删其他线程的锁
                releaseLock(lockKey, lockValue);
            }

        } catch (Exception e) {
            log.error("【计数器更新失败】环境：{}，主流程编号：{}，异常信息：", env, mainProcessId, e);
            throw new RuntimeException("计数器更新失败，请检查Redis连接或参数", e);
        }
    }

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    // 预编译脚本，提升性能
    private static final RedisScript<Long> UNLOCK_SCRIPT_OBJECT = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    /**
     * 释放Redis分布式锁（原子操作，防止误删）
     *
     * @param lockKey
     * @param lockValue
     * @return
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            Long result = redisTemplate.execute(
                    UNLOCK_SCRIPT_OBJECT,
                    Collections.singletonList(lockKey),
                    lockValue
            );

            boolean success = result != null && result == 1L;

            if (success) {
                log.debug("分布式锁释放成功，锁Key：{}", lockKey);
            } else {
                log.warn("分布式锁释放失败，锁Key：{}，可能锁已超时自动释放或不属于当前线程", lockKey);
            }

            return success;

        } catch (Exception e) {
            // 区分不同类型的异常
            if (e instanceof RedisSystemException) {
                log.error("Redis系统异常导致锁释放失败，锁Key：{}", lockKey, e);
            } else {
                log.error("未知异常导致锁释放失败，锁Key：{}", lockKey, e);
            }
            return false;
        }
    }

    /**
     * 提交流程
     *
     * @param ticketId
     * @param transitionId
     */
    public void submitProcess(String ticketId, String transitionId) {
        // 查询工单详情
        String ticketUrl = String.format("%s/scenediy/open/ticket/ticket/%s?apikey=%s",
                sceneDiyProperty.getUrl(), ticketId, sceneDiyProperty.getApiKey());
        JSONObject ticketObject = JSON.parseObject(HttpUtil.doRequestGet(ticketUrl, null));
        String taskId = ticketObject.getJSONObject("data").getJSONArray("current_task_ids").get(0).toString();

        // 提交工单参数
        JSONObject commitParam = new JSONObject();
        commitParam.put("task_log_id", taskId);
        commitParam.put("ticket_id", ticketId);
        commitParam.put("transition_id", transitionId);
        commitParam.put("fields", new JSONArray());

        String commitUrl = String.format("%s/scenediy/open/ticket/ticket/batch_proceed?apikey=%s",
                sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey());
        String requestPost = HttpUtil.doRequestPost(commitUrl, commitParam.toJSONString());
        JSONObject result = JSON.parseObject(requestPost);
        if (result != null && "OK".equals(result.getString("code"))) {
            log.info("提交工单成功！ticketId:{}，接口返回:{}", ticketId, requestPost);
        } else {
            log.error("提交工单失败！ticketId:{}，接口返回:{}", ticketId, requestPost);
        }
    }

    @Override
    public Map<String, Object> getKeyCounter(WorkFlowCounterDTO dto) {
        String env = sceneDiyProperty.getEnv();
        String mainProcessId = dto.getMainProcessId();

        // 构建Redis Key
        String counterKey = String.format(RedisKeyConstant.COUNTER_HASH_KEY, env, mainProcessId);

        // 一次性获取counterKey下所有Hash键值对（仅1次Redis调用，替代逐个get）
        Map<Object, Object> hashMap = redisTemplate.opsForHash().entries(counterKey);

        // 转换为<String, Object>（避免JSON键为Object类型，保证格式规范）
        Map<String, Object> jsonMap = new HashMap<>(hashMap.size());
        for (Map.Entry<Object, Object> entry : hashMap.entrySet()) {
            // 键转String（Redis Hash的键本身就是String，这里强转安全）
            String key = String.valueOf(entry.getKey());
            jsonMap.put(key, entry.getValue());
        }

        return jsonMap;
    }


    /**
     * 单次调用SAP同步接口，分布式维护update_on日期
     * 首次调用初始化：20190101，成功后自增1天，超出20260205则终止
     *
     * @return 接口调用结果（状态、当前日期、响应信息）
     */
    @Override
    public void syncSapSingleTime() {
        // 1. 基础配置：日期格式、起止日期
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        final LocalDate START_DATE = LocalDate.of(2019, 1, 1);
        final LocalDate END_DATE = LocalDate.of(2026, 3, 25);
        // 获取当前环境标识
        String env = sceneDiyProperty.getEnv();

        // 2. 构建Redis存储Key（环境隔离，兼容测试/生产）
        String redisDateKey = String.format(RedisKeyConstant.SAP_SYNC_CURRENT_DATE, env);

        try {
            // 3. 从Redis获取当前日期，无值则初始化起始日期
            String currentDateStr = redisTemplate.opsForValue().get(redisDateKey);
            LocalDate currentDate;
            if (StringUtil.isEmpty(currentDateStr)) {
                // 首次调用：设置初始日期
                currentDate = START_DATE;
                log.info("【SAP单次同步】Redis无历史日期，初始化起始日期：{}", currentDate.format(formatter));
            } else {
                // 解析Redis中存储的历史日期
                currentDate = LocalDate.parse(currentDateStr, formatter);
            }

            // 4. 边界校验：已超出结束日期，终止调用
            if (currentDate.isAfter(END_DATE)) {
                String msg = "已超出同步结束日期[20260205]，无需继续调用接口";
                log.warn("【SAP单次同步】{}", msg);
                return;
            }
            String updateOn = currentDate.format(formatter);

            // 5. 拼接接口请求参数，调用SAP同步接口
            String params = String.format("update_on=%s&apikey=84a4ef7499eb43c2862cf3ec35d6eb2e5e2e", updateOn);
            log.info("【SAP单次同步】开始调用接口，update_on：{}", updateOn);
            HttpUtil.doRequestGet("https://tenon-cmdb.sensetime.com/openapi/v1/connector/sync/sap", params);

            // 6. 核心逻辑：调用成功后，日期+1天，更新回Redis
            LocalDate nextDate = currentDate.plusDays(1);
            String nextDateStr = nextDate.format(formatter);
            // 异步/分布式场景：覆盖更新Redis日期，无过期时间（可根据业务添加）
            redisTemplate.opsForValue().set(redisDateKey, nextDateStr);
            log.info("【SAP单次同步】日期更新完成，下次调用update_on：{}", nextDateStr);

        } catch (Exception e) {
            // 全局异常捕获：调用失败，不更新Redis日期
            String msg = "SAP单次同步接口执行异常";
            log.error("【SAP单次同步】{}，异常信息：", msg, e);
        }

        return;
    }

    @Override
    public void deleteKey(String key) {
        try {
            // 删除Redis Key
            redisTemplate.delete(key);
            log.info("【RedisKey删除成功】Key值：{}", key);
        } catch (Exception e) {
            log.error("【RedisKey删除失败】Key值：{} 异常：", key, e);
            throw new RuntimeException("RedisKey删除失败", e);
        }
    }

    /**
     * 根据Redis Key 获取 Redis 中指定 Key 的 value
     *
     * @param redisKey
     * @return
     */
    @Override
    public Object getRedisValueByKey(String redisKey) {
        // 直接获取Redis中指定Key的 value（核心逻辑，1次Redis调用）
        return redisTemplate.opsForValue().get(redisKey);
    }

    @Override
    public void syncFullAsset() {
        List<PhysicalAsset> physicalAssets = queryModelData("physical_assets",
                Arrays.asList("id", "classCode", "name", "sn", "asset_code", "main_device_type", "u_number",
                        "Manufacturer", "ManufactModel", "host_type", "machine_code", "wh_location", "maintenance_end_time"),
                PhysicalAsset.class, new ArrayList<>());

        // 无更新数据
        if (CollectionUtils.isEmpty(physicalAssets)) {
            log.info("【资产定时同步】设备资产中无更新数据");
            return;
        }

        SyncAsset(physicalAssets);
    }

    @Override
    public void timeTriggerSyncAsset(String startTime, String endTime) {

        log.info("【资产定时同步】===== 开始 =====");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (StringUtil.isEmpty(startTime) && StringUtil.isEmpty(endTime)) {
            // 同步前一天的日志
            LocalDate day = LocalDate.now().minusDays(1);
            startTime = day.atStartOfDay().format(formatter);
            endTime = day.atTime(23, 59, 59).format(formatter);
        }
        List<String> timeRange = Arrays.asList(startTime, endTime);

        CmdbModelParams.QueryCondition assetQueryCondition = new CmdbModelParams.QueryCondition();
        assetQueryCondition.setField("updateTime");
        assetQueryCondition.setOperator("RANGE");
        assetQueryCondition.setValue(timeRange);

        List<CmdbModelParams.QueryCondition> assetConditions = new ArrayList<>();
        assetConditions.add(assetQueryCondition);

        List<PhysicalAsset> physicalAssets = queryModelData("physical_assets",
                Arrays.asList("id", "classCode", "name", "sn", "asset_code", "main_device_type", "u_number",
                        "Manufacturer", "ManufactModel", "host_type", "machine_code", "wh_location", "maintenance_end_time"),
                PhysicalAsset.class, assetConditions);

        // 无更新数据
        if (CollectionUtils.isEmpty(physicalAssets)) {
            log.info("【资产定时同步】设备资产中无更新数据");
            return;
        }

        SyncAsset(physicalAssets);
    }

    /**
     * 同步资产
     *
     * @param physicalAssets
     */
    public void SyncAsset(List<PhysicalAsset> physicalAssets) {

        log.info("【资产定时同步】===== 开始执行资产自动同步任务 =====");

        Map<String, PhysicalAsset> physicalAssetMap = physicalAssets
                .stream()
                // 核心：toMap 直接实现 分组+去重+取第一个
                .collect(Collectors.toMap(
                        PhysicalAsset::getSn,
                        asset -> asset,
                        (first, second) -> first
                ));

        log.info("【资产定时同步】物理资产按SN去重完成，有效资产数：{}", physicalAssetMap.size());

        CmdbModelParams.QueryCondition serverQueryCondition = new CmdbModelParams.QueryCondition();
        serverQueryCondition.setField("name");
        serverQueryCondition.setOperator("IN");
        serverQueryCondition.setValue(physicalAssetMap.keySet());

        List<CmdbModelParams.QueryCondition> serverConditions = new ArrayList<>();
        serverConditions.add(serverQueryCondition);

        List<SenseCoreServer> senseCoreServers = queryModelData("SenseCore_Server",
                Arrays.asList("id", "name"), SenseCoreServer.class, serverConditions);

        List<SenseCoreSwitch> senseCoreSwitches = queryModelData("SenseCore_Switch",
                Arrays.asList("id", "name"), SenseCoreSwitch.class, serverConditions);

        // 无可匹配数据
        if (CollectionUtils.isEmpty(senseCoreServers) && CollectionUtils.isEmpty(senseCoreSwitches)) {
            log.info("【资产定时同步】未查询到匹配的服务器和交换机数据，同步任务结束");
            return;
        }

        log.info("【资产定时同步】查询到匹配服务器数量：{}，交换机数量：{}", CollectionUtils.size(senseCoreServers), CollectionUtils.size(senseCoreSwitches));

        // 待更新数据(SenseCore服务器/SenseCore交换机)
        List<Map<String, Object>> needUpdateList = new ArrayList<>();

        for (SenseCoreServer senseCoreServer : senseCoreServers) {
            PhysicalAsset physicalAsset = physicalAssetMap.get(senseCoreServer.getName());
            if (physicalAsset == null) {
                log.info("【资产定时同步】服务器[name={}]未匹配到对应物理资产，跳过更新", senseCoreServer.getName());
                continue;
            }
            Map<String, Object> diyMap = new HashMap<>();
            diyMap.put("id", senseCoreServer.getId());
            diyMap.put("classCode", "SenseCore_Server");
            diyMap.put("asset_code", physicalAsset.getAsset_code());
            diyMap.put("main_device_type", physicalAsset.getMain_device_type());
            diyMap.put("u_number", physicalAsset.getU_number());
            diyMap.put("host_type", physicalAsset.getHost_type());
            diyMap.put("Manufacturer", physicalAsset.getManufacturer());
            diyMap.put("ManufactModel", physicalAsset.getManufactModel());
            diyMap.put("machine_code", physicalAsset.getMachine_code());
            diyMap.put("wh_location", physicalAsset.getWh_location());
            diyMap.put("maintenance_end_time", physicalAsset.getMaintenance_end_time());
            needUpdateList.add(diyMap);
        }

        for (SenseCoreSwitch senseCoreSwitch : senseCoreSwitches) {
            PhysicalAsset physicalAsset = physicalAssetMap.get(senseCoreSwitch.getName());
            if (physicalAsset == null) {
                log.warn("【资产定时同步】交换机[name={}]未匹配到对应物理资产，跳过更新", senseCoreSwitch.getName());
                continue;
            }
            Map<String, Object> diyMap = new HashMap<>();
            diyMap.put("id", senseCoreSwitch.getId());
            diyMap.put("classCode", "SenseCore_Switch");
            diyMap.put("asset_code", physicalAsset.getAsset_code());
            diyMap.put("main_device_type", physicalAsset.getMain_device_type());
            diyMap.put("u_number", physicalAsset.getU_number());
            diyMap.put("host_type", physicalAsset.getHost_type());
            diyMap.put("Manufacturer", physicalAsset.getManufacturer());
            diyMap.put("ManufactModel", physicalAsset.getManufactModel());
            diyMap.put("machine_code", physicalAsset.getMachine_code());
            diyMap.put("wh_location", physicalAsset.getWh_location());
            diyMap.put("maintenance_end_time", physicalAsset.getMaintenance_end_time());
            needUpdateList.add(diyMap);
        }

        log.info("【资产定时同步】构建完成,SenseCore服务器与SenseCore交换机待更新数据总量：{}", needUpdateList.size());

        // 分批更新数据
        if (CollectionUtils.isNotEmpty(needUpdateList)) {
            String apiUrl = String.format("%s/store/openapi/v2/resources/batch_save?apikey=%s&source=sap",
                    sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey());
            int totalSize = needUpdateList.size();
            log.info("【资产定时同步】开始分批推送更新，总数据量：{}，每批次500条", totalSize);

            for (int i = 0; i < totalSize; i += 500) {
                int endIndex = Math.min(i + 500, totalSize);
                List<Map<String, Object>> batchData = needUpdateList.subList(i, endIndex);
                int currentBatchSize = batchData.size();
                int batchNum = (i / 500) + 1;
                try {
                    HttpUtil.doRequestPost(apiUrl, JSON.toJSONString(batchData));
                    log.info("【资产定时同步】第{}批数据推送成功，批次数量：{}", batchNum, currentBatchSize);
                } catch (Exception e) {
                    log.error("【资产定时同步】第{}批数据推送失败，批次数量：{}", batchNum, currentBatchSize, e);
                }
            }
        }
        log.info("【资产定时同步】===== 所有数据推送完成，同步任务结束 =====");
    }

    /**
     * 查询模型数据
     *
     * @param classCode
     * @param requiredFields
     * @param clazz
     * @param addConditions
     * @param <T>
     * @return
     */
    private <T> List<T> queryModelData(String classCode, List<String> requiredFields, Class<T> clazz, List<CmdbModelParams.QueryCondition> addConditions) {
        // 初始化结果集
        List<T> totalDataList = new ArrayList<>();

        // 1. 构建查询条件
        CmdbModelParams.QueryCondition queryCondition = new CmdbModelParams.QueryCondition();
        queryCondition.setField("classCode");
        queryCondition.setValue(classCode);
        List<CmdbModelParams.QueryCondition> conditions = new ArrayList<>();
        conditions.add(queryCondition);
        // 额外的查询条件
        if (CollectionUtils.isNotEmpty(addConditions)) conditions.addAll(addConditions);
        // 2. 生成CMDB查询参数
        CmdbModelParams cmdbModelParams = generateCmdbModelParams(conditions, requiredFields);

        // 3. 分页循环查询
        int page = 1;
        while (true) {
            String requestPost = HttpUtil.doRequestPost(
                    String.format("%s/store/openapi/v2/resources/query?apikey=%s", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
                    JSON.toJSONString(cmdbModelParams));

            // 获取数据为空，终止循环
            if (StringUtil.isEmpty(requestPost)) break;

            JSONObject jsonObject = JSON.parseObject(requestPost);
            // 获取数据对象为空，终止循环
            if (Objects.isNull(jsonObject)) break;

            Integer totalRecords = jsonObject.getInteger("totalRecords");
            JSONArray dataArray = jsonObject.getJSONArray("dataList");
            // 未查询到数据，终止循环
            if (totalRecords == null || totalRecords <= 0) break;
            if (dataArray == null || dataArray.isEmpty()) break;

            // 将数据添加到总集中（直接用JSONArray解析，避免转义问题）
            List<T> currentPageData = JSON.parseArray(dataArray.toJSONString(), clazz);
            if (currentPageData != null && !currentPageData.isEmpty()) {
                totalDataList.addAll(currentPageData);
            }

            // 已获取所有数据，终止循环
            if (page * 1000 >= totalRecords) break;
            // 更新页码
            cmdbModelParams.setPageNum(page);
            // 页码+1（因为cmdb的页码是从0开始，所以最后+1）
            page++;
        }

        return totalDataList;
    }

    /**
     * 生成cmdb模型查询参数
     *
     * @param conditions
     * @param requiredFields
     * @return
     */
    public CmdbModelParams generateCmdbModelParams(List<CmdbModelParams.QueryCondition> conditions, List<String> requiredFields) {
        // 初始化查询参数
        CmdbModelParams cmdbModelParams = new CmdbModelParams();
        // 添加查询条件
        cmdbModelParams.setConditions(conditions);
        // 添加返回参数
        cmdbModelParams.setRequiredFields(requiredFields);
        return cmdbModelParams;
    }

}
