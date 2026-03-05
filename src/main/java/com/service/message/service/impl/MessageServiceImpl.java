package com.service.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.service.message.dto.WorkFlowCounterDTO;
import com.service.message.property.MessageTemplate;
import com.service.message.property.SceneDiyProperty;
import com.service.message.property.WechatProperty;
import com.service.message.service.BusinessService;
import com.service.message.service.MessageService;
import com.service.message.utils.HttpUtil;
import com.service.message.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * @Author: ninth-sun
 * @Date: 2025/6/18 15:56
 * @Description: 消息服务接口实现类
 */
@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

//    private static List<String> serviceIds = new ArrayList<>();

    @Autowired
    private WechatProperty wechatProperty;

    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    @Autowired
    private BusinessService businessService;

    /**
     * 根据工单id发送消息
     *
     * @param ticketId
     */
    @Override
    public void send(String ticketId) {
        // 获取企微accessToken
        String accessToken = getAccessToken();
        // 获取流程信息
        Map<String, String> map = getProcessInfo(ticketId, accessToken);
        if (MapUtils.isEmpty(map)) return;
        // 根据流程状态生成消息描述
        String description = this.generateDescription(map.get("current_status"), map);
        // 企微回调地址
        String url = String.format("https://tenon-scenediy.sensetime.com/redirect.html?id=%s", ticketId);
        String content = String.format("{\"title\": \"%s\",\"description\": \"%s\",\"url\": \"%s\",\"btntxt\": \"点击处理\"}",
                map.get("title"), description, url);
        // 发送消息卡片
        String requestBody = String.format("{\"touser\":\"%s\",\"msgtype\":\"textcard\",\"agentid\":%s,\"textcard\":%s}",
                map.get("userIds"), wechatProperty.getAgentId(), content);
        HttpUtil.doRequestPost(wechatProperty.getUrl() + "/message/send?access_token=" + accessToken, requestBody);
    }

    /**
     * 生成消息描述
     *
     * @param status
     * @param map
     * @return
     */
    public String generateDescription(String status, Map<String, String> map) {
        // 根据流程状态获取消息模版code
        String code;
        switch (status) {
            case "SUSPEND":
                // 挂起
                code = "SUSPEND";
                break;
            case "WITHDRAW":
                // 撤单
                code = "WITHDRAW";
                break;
            case "TERMINATION":
                code = "TERMINATION";
                break;
            default:
                // 默认为RUNNING
                code = "RUNNING";
        }

        // 创建消息模板
        MessageTemplate template = MessageTemplate.getByCode(code);
        String description;
        // 根据code生成不同描述
        switch (code) {
            case "RUNNING":
                description = template.generateMessageByCode(
                        map.get("create_time"),
                        map.get("sn"),
                        map.get("current_step"),
                        map.get("service_name"),
                        map.get("current_processor"));
                break;
            case "SUSPEND":
            case "WITHDRAW":
            case "TERMINATION":
                description = template.generateMessageByCode(
                        map.get("create_time"),
                        map.get("sn"),
                        map.get("current_processor"));
                break;
            default:
                description = "";
        }

        return description;
    }

    /**
     * 自定义发送消息
     *
     * @param userAccounts
     * @param msgtype
     * @param content
     */
    @Override
    public void sendCustomMessage(List<String> userAccounts, String msgtype, String content) {
        // 获取企微accessToken
        String accessToken = getAccessToken();

        // 发送用户
        String touser = "";
        if (CollectionUtils.isNotEmpty(userAccounts)) {
            StringBuffer sb = new StringBuffer();
            for (String account : userAccounts) {
                // 根据用户账号获取用户邮箱
                String emailByAccount = getUserEmailByAccount(account);
                if (StringUtil.isNotEmpty(emailByAccount)) {
                    // 根据用户邮箱获取用户企微id
                    String userId = getUserIdByEmail(emailByAccount, accessToken);
                    if (StringUtil.isNotEmpty(userId)) {
                        sb.append(userId).append("|");
                    }
                }
            }
            if (sb.length() > 0) touser = sb.substring(0, sb.length() - 1);
        }


        // 发送消息
        String requestBody = String.format("{\"touser\":\"%s\",\"msgtype\":\"%s\",\"agentid\":%s,\"%s\":%s}",
                touser, msgtype, wechatProperty.getAgentId(), msgtype, content);

        HttpUtil.doRequestPost(wechatProperty.getUrl() + "/message/send?access_token=" + accessToken, requestBody);
    }

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

    public Map<String, String> getProcessInfo(String ticketId, String accessToken) {
        Map<String, String> map = new HashMap<>();
        // 查询工单详情
        String ticketUrl = String.format("%s/scenediy/open/ticket/ticket/get_ticket_detail?ticket_id=%s&apikey=%s",
                sceneDiyProperty.getUrl(), ticketId, sceneDiyProperty.getApiKey());
        JSONObject ticketObject = JSON.parseObject(HttpUtil.doRequestGet(ticketUrl, null));

        JSONObject data = ticketObject.getJSONObject("data");
        if (ObjectUtils.isEmpty(data)) return map;


        JSONArray stateList = data.getJSONArray("state_list");
        if (CollectionUtils.isEmpty(stateList)) return map;

        JSONObject lastObj = stateList.getJSONObject(stateList.size() - 1);
        if (ObjectUtils.isEmpty(lastObj)) return map;

        // 主流程自动创建子流程节点不发送消息
        if ("SUB_FLOW".equals(lastObj.getString("type"))) return map;

        // 查询工单信息
        String params = String.format("apikey=%s", sceneDiyProperty.getApiKey());
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getUrl() + "/scenediy/open/ticket/ticket/" + ticketId, params);
        StringBuffer sb = new StringBuffer();

        JSONObject jsonObject = JSON.parseObject(requestGet);
        if (ObjectUtils.isEmpty(jsonObject)) return map;

        JSONObject dataJson = jsonObject.getJSONObject("data");
        if (ObjectUtils.isEmpty(dataJson)) return map;

        String processor = dataJson.getString("current_processors");
        if (StringUtil.isNotEmpty(processor)) {
            // 按逗号分割为数组
            String[] parts = processor.split(",");
            // 存储处理后的结果
            String[] results = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                // 分割每段字符串，获取斜杠后的部分
                String[] segment = parts[i].split("/");
                // 如果包含斜杠，则取斜杠后的部分，否则取原字符串
                results[i] = segment.length > 1 ? segment[1] : parts[i];
            }
            // 处理用户账号数组
            for (String result : results) {
                // 根据用户账号获取用户邮箱
                String emailByAccount = getUserEmailByAccount(result);
                if (StringUtil.isNotEmpty(emailByAccount)) {
                    // 根据用户邮箱获取用户企微id
                    String userId = getUserIdByEmail(emailByAccount, accessToken);
                    if (StringUtil.isNotEmpty(userId)) {
                        sb.append(userId).append("|");
                    }
                }
            }
        }
        if (sb.length() > 0) map.put("userIds", sb.substring(0, sb.length() - 1));
        map.put("sn", dataJson.getString("sn"));
        map.put("create_time", dataJson.getString("create_at"));
        map.put("title", dataJson.getString("title"));
        map.put("service_name", dataJson.getString("service_name"));
        map.put("current_step", dataJson.getString("previous_state_name"));
        map.put("current_processor", dataJson.getString("current_processors"));
        map.put("current_status", dataJson.getString("current_status"));


//        // 初始化子工单服务id
//        if (CollectionUtils.isEmpty(serviceIds)) {
//            serviceIds.addAll(Arrays.asList(sceneDiyProperty.getBatchServiceId().split("-")));
//        }
//
//        // 如果当前工单为批量处理的子工单且状态为已拒绝
//        if (serviceIds.contains(dataJson.getString("service_id")) && "TERMINATION".equals(dataJson.getString("current_status"))) {
//            this.handleRejectTicket(data.getJSONObject("data_form"));
//        }

        return map;
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

    public String getUserEmailByAccount(String account) {
        String params = String.format("account=%s&apikey=%s", account, sceneDiyProperty.getApiKey());
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getUrl() + "/tenant/openapi/v2/users/find_by_account", params);
        String userEmail = null;
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            if (jsonObject != null) {
                userEmail = jsonObject.getString("email");
            }
        }
        return userEmail;
    }

    /**
     * 处理拒绝工单
     *
     * @param dataForm
     */
    public void handleRejectTicket(JSONObject dataForm) {
        if (ObjectUtils.isEmpty(dataForm)) return;

        String mainProcessId = dataForm.getString("relatedMainTicketNo");
        if (StringUtil.isNotEmpty(mainProcessId)) {
            businessService.updateCounter(WorkFlowCounterDTO.builder().mainProcessId(mainProcessId).operation("reject").build());
        }
    }
}
