package com.service.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.service.message.constant.CommonConstant;
import com.service.message.property.SceneDiyProperty;
import com.service.message.service.ProcessMessageService;
import com.service.message.utils.HttpUtil;
import com.service.message.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * @Author: ninth-sun
 * @Date: 2025/8/18 17:38
 * @Description: 流程消息接口实现类
 */
@Service
public class ProcessMessageServiceImpl implements ProcessMessageService {

    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    @Override
    public String generateCheckReceiveProcess(String number) {
        String oaToken = getOaToken();
        String params = String.format("workFlowNumber=%s", number);
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("auth_token", oaToken);
        // 查询oa的到货验收数据
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getOaUrl() + "/operation/erp/cmdb/getAcceptanceData", params, headerMap);
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            JSONArray data = jsonObject.getJSONArray("data");
            if (CollectionUtils.isNotEmpty(data) && 2000 == jsonObject.getInteger("statusCode")) {
                for (Object o : data) {
                    JSONObject dataObject = (JSONObject) o;
                    String requestBody = String.format("{\"purchase_check_oa_no\":\"%s\",\"po_no\":\"%s\",\"po_line_no\":\"%s\",\"purch\":\"%s\",\"asset_sns\":%s}",
                            dataObject.getString("prSingleNumber"), dataObject.getString("purchaseOrderNumber"), dataObject.getString("purchaseOrderItemNumber"),
                            dataObject.getString("purchaseBP"), dataObject.getJSONArray("fixedAssetNumbers"));
                    // 根据到货验收数据创建scenediy的流程
                    HttpUtil.doRequestPost(sceneDiyProperty.getUrl() + "/custom/serviceapi/v1/createArriveOrder?apikey=" + sceneDiyProperty.getApiKey(), requestBody);
                }
            }
        }


        return CommonConstant.SUCCESS;
    }

    /**
     * 获取oa的token
     * @return
     */
    public String getOaToken() {
        String requestPost = HttpUtil.doRequestPost(sceneDiyProperty.getOaUrl() + "/v1/getToken",
                "{\"username\":\"CMDB\",\"password\":\"" + sceneDiyProperty.getOaPassword() + "\"}");
        String token = "";
        if (StringUtil.isNotEmpty(requestPost)) {
            JSONObject jsonObject = JSON.parseObject(requestPost);
            JSONObject data = jsonObject.getJSONObject("data");
            if (data != null && 2000 == jsonObject.getInteger("statusCode")) {
                token = data.getString("token");
            }
        }
        return token;
    }


    public static void main(String[] args) {

        String requestGet = "{\n" +
                "  \"statusCode\": 2000,\n" +
                "  \"msg\": \"ok\",\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"workFlowNumber\": \"CGYS-202507-0012\",\n" +
                "      \"prSingleNumber\": \"CGSQ-202507-1001\",\n" +
                "      \"purchaseOrderNumber\": \"450004621\",\n" +
                "      \"purchaseOrderItemNumber\": \"0010\",\n" +
                "      \"quantity\": \"2\",\n" +
                "      \"fixedAssetNumbers\": [\n" +
                "        \"CN1616017530\",\n" +
                "        \"CN1616017531\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"timestamp\": 1747116829379\n" +
                "}";
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            JSONArray data = jsonObject.getJSONArray("data");
            if (CollectionUtils.isNotEmpty(data) && 2000 == jsonObject.getInteger("statusCode")) {
                for (Object o : data) {
                    JSONObject dataObject = (JSONObject) o;
                    String requestBody = String.format("{\"purchase_check_oa_no\":\"%s\",\"po_no\":\"%s\",\"po_line_no\":\"%s\",\"purch\":\"%s\",\"asset_sns\":%s}",
                            dataObject.getString("prSingleNumber"), dataObject.getString("purchaseOrderNumber"), dataObject.getString("purchaseOrderItemNumber"),
                            dataObject.getString("purchaseBP"), dataObject.getJSONArray("fixedAssetNumbers"));
                    System.out.println(1);
                }
            }
        }


    }

}
