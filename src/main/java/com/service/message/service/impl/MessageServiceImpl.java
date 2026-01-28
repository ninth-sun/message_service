package com.service.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.service.message.property.MessageTemplate;
import com.service.message.property.SceneDiyProperty;
import com.service.message.property.WechatProperty;
import com.service.message.service.MessageService;
import com.service.message.utils.HttpUtil;
import com.service.message.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: ninth-sun
 * @Date: 2025/6/18 15:56
 * @Description: 消息服务接口实现类
 */
@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

    @Autowired
    private WechatProperty wechatProperty;

    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    /**
     * 根据工单id发送消息
     * @param ticketId
     */
    @Override
    public void send(String ticketId) {
        // 获取企微accessToken
        String accessToken = getAccessToken();
        // 获取流程信息
        Map<String, String> map = getProcessInfo(ticketId, accessToken);
        // 创建消息模板
        MessageTemplate template = MessageTemplate.getByCode("SCENE_DIY");
        String description = template.generateMessageByCode(
                map.get("create_time"), map.get("sn"), map.get("current_step"), map.get("current_processor"));
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
     * 自定义发送消息
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
        String params = String.format("apikey=%s", sceneDiyProperty.getApiKey());
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getUrl() + "/scenediy/open/ticket/ticket/" + ticketId, params);
        StringBuffer sb = new StringBuffer();
        Map<String, String> map = new HashMap<>();
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            if (jsonObject != null) {
                String data = jsonObject.getString("data");
                if (StringUtil.isNotEmpty(data)) {
                    JSONObject dataJson = JSON.parseObject(data);
                    if (dataJson != null) {
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
                    }
                }
            }
        }
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
}
