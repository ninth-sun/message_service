package com.service.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.service.message.constant.CommonConstant;
import com.service.message.pojo.*;
import com.service.message.property.SceneDiyProperty;
import com.service.message.service.ProcessMessageService;
import com.service.message.utils.HttpUtil;
import com.service.message.utils.SmcUtil;
import com.service.message.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: ninth-sun
 * @Date: 2025/8/18 17:38
 * @Description: 流程消息接口实现类
 */
@Service
@Slf4j
public class ProcessMessageServiceImpl implements ProcessMessageService {

    private Integer catalogId = 32;

    private Integer serviceId = 112;

    private Integer stockCatalogId = 32;

    private Integer stockServiceId = 78;

    /**
     * 查询资源数据
     */
    public final static String RES_QUERY_URL = "%s/store/openapi/v2/resources/query?apikey=%s";

    /**
     * diy创建工单
     */
    public final static String DIY_COMMIT_URL = "%s/scenediy/open/ticket/ticket/create_ticket_and_auto_proceed?apikey=%s";

    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    /**
     * 获取oa的token
     *
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

    @Override
    @Async
    public String generateCheckReceiveProcess(String number) {
        String oaToken = getOaToken();
        String params = String.format("workFlowNumber=%s", number);
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("Content-Type", "application/json");
        headerMap.put("auth_token", oaToken);
        // 查询OA的到货验收数据
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getOaUrl() + "/operation/erp/cmdb/getAcceptanceData", params, headerMap);
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            JSONArray data = jsonObject.getJSONArray("data");
            if (CollectionUtils.isNotEmpty(data) && 2000 == jsonObject.getInteger("statusCode")) {
                for (Object o : data) {
                    JSONObject dataObject = (JSONObject) o;
                    // 资产编号列表
                    JSONArray fixedAssetNumbers = dataObject.getJSONArray("fixedAssetNumbers");
                    // 如果不添加默认编号,DIY解析会报错
                    if (CollectionUtils.isEmpty(fixedAssetNumbers)) {
                        fixedAssetNumbers = new JSONArray();
                        // 添加默认固定资产编号
                        fixedAssetNumbers.add("DEFAULT-FIXED-ASSET-NUMBER");
                    }
                    String requestBody = String.format("{\"purchase_check_oa_no\":\"%s\",\"po_no\":\"%s\",\"po_line_no\":\"%s\",\"purch\":\"%s\",\"asset_sns\":%s}",
                            dataObject.getString("workFlowNumber"), dataObject.getString("purchaseOrderNumber"), dataObject.getString("purchaseOrderItemNumber"),
                            dataObject.getString("purchaseBP"), fixedAssetNumbers);
                    // 根据到货验收数据创建scenediy的流程
                    TreeMap<String, String> diyHeaderMap = new TreeMap();
                    diyHeaderMap.put("apikey", sceneDiyProperty.getApiKey());
                    SmcUtil.smcSendPost(sceneDiyProperty.getSmcUrl() + "/custom/serviceapi/v1/createArriveOrder",
                            sceneDiyProperty.getSmcAk(), sceneDiyProperty.getSmcSk(), diyHeaderMap, requestBody);
                }
            }
        }
        return CommonConstant.SUCCESS;
    }

    @Override
    @Async
    public void generateBuyRequestProcess(String number) {
        String oaToken = getOaToken();
        String params = String.format("prSingleNumber=%s", number);
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("Content-Type", "application/json");
        headerMap.put("auth_token", oaToken);
        // 查询OA的采购需求申请数据
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getOaUrl() + "/operation/erp/cmdb/processDataSyn", params, headerMap);
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            JSONArray data = jsonObject.getJSONArray("data");
            if (CollectionUtils.isNotEmpty(data) && 2000 == jsonObject.getInteger("statusCode")) {

                Map<String, List<JSONObject>> poGroupMap = new HashMap<>();
                for (Object o : data) {
                    JSONObject dataObject = (JSONObject) o;
                    String poNumber = dataObject.getString("poNumber");
                    // 不存在则创建集合
                    poGroupMap.computeIfAbsent(poNumber, k -> new ArrayList<>());
                    poGroupMap.get(poNumber).add(dataObject);
                }

                // 遍历分组后的PO单（一个PO号仅执行1次接口请求）
                for (Map.Entry<String, List<JSONObject>> entry : poGroupMap.entrySet()) {
                    String poNumber = entry.getKey();
                    List<JSONObject> poLineList = entry.getValue();
                    // 取第一个数据的公共字段（user/pr单号等，同一个PO号一致）
                    JSONObject firstData = poLineList.get(0);
                    String user = firstData.getString("purchaseBP");
                    String prSingleNumber = firstData.getString("prSingleNumber");

                    // 存放当前PO号的所有行数据
                    List<Map<String, Object>> arrivalPlanList = new ArrayList<>();

                    // 遍历当前PO的所有行
                    for (JSONObject dataObject : poLineList) {
                        // 产品需求名称
                        String productRequirementsName = dataObject.getString("productRequirementsName");
                        // 厂商
                        String deliveryManufacturer = dataObject.getString("deliveryManufacturer");
                        // 规格型号
                        String specificationsAndModels = dataObject.getString("specificationsAndModels");
                        // 下单数量
                        Integer orderQuantity = dataObject.getInteger("orderQuantity");
                        // po行数据（只有1个对象，直接取第一个）
                        JSONArray poItemDetails = dataObject.getJSONArray("poItemDetails");
                        if (CollectionUtils.isNotEmpty(poItemDetails)) {
                            JSONObject itemDetail = poItemDetails.getJSONObject(0);
                            // 资产编号集合
                            JSONArray assetObject = itemDetail.getJSONArray("assetObject");
                            // 编号
                            String poLine = itemDetail.getString("poItem");
                            String name = String.format("%s-%s-P1", poNumber, poLine);

                            // 构建arrival_plan单个对象
                            Map<String, Object> arrivalPlanMap = new HashMap<>();
                            arrivalPlanMap.put("name", name);
                            arrivalPlanMap.put("pr_no", prSingleNumber);
                            arrivalPlanMap.put("po_no", poNumber);
                            arrivalPlanMap.put("po_line_no", poLine);
                            arrivalPlanMap.put("product", productRequirementsName);
                            arrivalPlanMap.put("supplier", "");
                            arrivalPlanMap.put("vendor", deliveryManufacturer);
                            arrivalPlanMap.put("model", specificationsAndModels);
                            arrivalPlanMap.put("procurement_business_partner", List.of(user));
                            arrivalPlanMap.put("batch_no", "1");
                            arrivalPlanMap.put("procurement_amount", orderQuantity);
                            arrivalPlanMap.put("asset_code_list", assetObject);
                            arrivalPlanMap.put("asset_count", CollectionUtils.isEmpty(assetObject) ? 0 : assetObject.size());

                            // 添加到当前PO的行集合
                            arrivalPlanList.add(arrivalPlanMap);
                        }
                    }

                    // 统一发送请求（一个PO号仅1次）
                    if (CollectionUtils.isNotEmpty(arrivalPlanList)) {
                        // 构建field_dict和外层Map
                        Map<String, Object> fieldDict = new HashMap<>();
                        fieldDict.put("users", List.of(user));
                        fieldDict.put("arrival_plan", arrivalPlanList);
                        // 需要资产管理节点参数
                        fieldDict.put("need_asset_management", "yes");

                        Map<String, Object> requestBodyMap = new HashMap<>();
                        requestBodyMap.put("catalog_id", "32");
                        requestBodyMap.put("service_id", "112");
                        requestBodyMap.put("field_dict", fieldDict);

                        // 序列化JSON
                        String requestBody = JSON.toJSONString(requestBodyMap);

                        // 根据采购需求申请数据创建diy的流程
                        TreeMap<String, String> diyHeaderMap = new TreeMap();
                        diyHeaderMap.put("apikey", sceneDiyProperty.getApiKey());
                        SmcUtil.smcSendPost(sceneDiyProperty.getSmcUrl() + "/scenediy/open/ticket/ticket/create_ticket_and_auto_proceed",
                                sceneDiyProperty.getSmcAk(), sceneDiyProperty.getSmcSk(), diyHeaderMap, requestBody);
                    }
                }
            }
        }
    }

    @Override
    public void generateBuyRequestProcessByTime() {
        Map<String, Object> queryParam = getQueryParamMap("pr_no");
        // 获取cmdb中的到货计划数据
        String resOut = getResOut(sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey(), JSON.toJSONString(queryParam));
        JSONArray dataList = JSON.parseObject(resOut).getJSONArray("dataList");
        List<ArrivalPlan> list = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {

            JSONObject data = dataList.getJSONObject(i);
            ArrivalPlan plan = new ArrivalPlan();
            plan.setName(data.getString("name"));
            plan.setPr_no(data.getString("pr_no"));
            plan.setPo_no(data.getString("po_no"));
            plan.setPo_line_no(data.getString("po_line_no"));
            plan.setProduct(data.getString("product"));
            plan.setSupplier(data.getString("supplier"));
            plan.setVendor(data.getString("vendor"));
            plan.setModel(data.getString("model"));
            JSONArray pba = data.getJSONArray("procurement_business_partner");
            plan.setProcurement_amount(data.getString("procurement_amount"));
            if (Objects.nonNull(pba)) {
                String account = pba.getJSONObject(0).getString("account");
                plan.setProcurement_business_partner(Collections.singletonList(account));
                plan.setProcurement_business_partner_str(account);
            }
            String batchNo = StringUtils.isNotEmpty(data.getString("batch_no")) ? data.getString("batch_no") : "1";
            plan.setBatch_no(batchNo);
            if (CollectionUtils.isNotEmpty(plan.getProcurement_business_partner())) {
                list.add(plan);
            }
        }
        Map<String, List<ArrivalPlan>> map = list.stream().collect(Collectors.groupingBy(ArrivalPlan::getProcurement_business_partner_str));
        // 分组之后提交工单
        for (Map.Entry<String, List<ArrivalPlan>> entry : map.entrySet()) {
            // 组装数据
            List<ArrivalPlan> arrivalPlans = entry.getValue();
            Map<String, Object> diyMap = new HashMap<>();
            diyMap.put("catalog_id", catalogId);
            diyMap.put("service_id", serviceId);
            Map<String, Object> field_dict = new HashMap<>();
            field_dict.put("users", arrivalPlans.get(0).getProcurement_business_partner());
            field_dict.put("arrival_plan", arrivalPlans);
            diyMap.put("field_dict", field_dict);
            commitDiy(sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey(), JSON.toJSONString(diyMap));
        }
    }

    @Override
    public void generateStockRequestProcessByTime() {
        Map<String, Object> queryParam = getQueryParamMap("oa_stock_order_no");
        String resOut = getResOut(sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey(), JSON.toJSONString(queryParam));
        JSONArray dataList = JSON.parseObject(resOut).getJSONArray("dataList");
        List<ArrivalPlan> list = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {
            JSONObject data = dataList.getJSONObject(i);
            ArrivalPlan plan = new ArrivalPlan();
            plan.setName(data.getString("name"));
            plan.setOa_stock_order_no(data.getString("oa_stock_order_no"));
            plan.setMaterial_no(data.getString("material_no"));
            plan.setPo_no(data.getString("po_no"));
            plan.setPo_line_no(data.getString("po_line_no"));
            plan.setHost_type(data.getString("host_type"));
            plan.setProduct(data.getString("product"));
            plan.setSupplier(data.getString("supplier"));
            plan.setLocation(data.getString("location"));
            plan.setCompany_code(data.getString("company_code"));
            plan.setProcurement_amount(data.getString("procurement_amount"));
            plan.setExpect_arrival_date(data.getString("expect_arrival_date"));
            plan.setExpect_arrival_amount(data.getString("expect_arrival_amount"));
            plan.setInclude_rack_install(data.getString("include_rack_install"));
            JSONArray pba = data.getJSONArray("plan_procurement_business_partner");
            if (Objects.nonNull(pba)) {
                String account = pba.getJSONObject(0).getString("account");
                plan.setPlan_procurement_business_partner(Collections.singletonList(account));
                plan.setPlan_procurement_business_partner_str(account);
            }
            String batchNo = StringUtils.isNotEmpty(data.getString("batch_no")) ? data.getString("batch_no") : "1";
            plan.setBatch_no(batchNo);
            if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(plan.getPlan_procurement_business_partner())) {
                list.add(plan);
            }
        }
        Map<String, List<ArrivalPlan>> map = list.stream().collect(Collectors.groupingBy(ArrivalPlan::getPlan_procurement_business_partner_str));
        // 分组之后提交工单
        for (Map.Entry<String, List<ArrivalPlan>> entry : map.entrySet()) {
            // 组装数据
            List<ArrivalPlan> arrivalPlans = entry.getValue();
            Map<String, Object> diyMap = new HashMap<>();
            diyMap.put("catalog_id", stockCatalogId);
            diyMap.put("service_id", stockServiceId);
            Map<String, Object> field_dict = new HashMap<>();
            field_dict.put("users", arrivalPlans.get(0).getPlan_procurement_business_partner());
            field_dict.put("arrival_plan", arrivalPlans);
            diyMap.put("field_dict", field_dict);
            commitDiy(sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey(), JSON.toJSONString(diyMap));
        }
    }

    public static Map<String, Object> getQueryParamMap(String field) {
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("pageSize", 999);
        // 创建conditions列表
        List<Map<String, Object>> conditions = new ArrayList<>();
        // 创建第一个条件并添加到conditions
        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("field", "classCode");
        condition1.put("value", "arrival_plan");
        conditions.add(condition1);
        // 创建第二个组合条件
        Map<String, Object> condition2 = new HashMap<>();
        condition2.put("cjt", "AND");
        // 创建组合条件中的子条件列表
        List<Map<String, Object>> subConditions = new ArrayList<>();
        Map<String, Object> subCondition = new HashMap<>();
        subCondition.put("field", field);
        subCondition.put("operator", "IS_NOT_NULL");
        subConditions.add(subCondition);
        // 将子条件列表添加到组合条件
        condition2.put("items", subConditions);
        // 将组合条件添加到conditions
        conditions.add(condition2);
        // 将conditions添加到最外层Map
        queryParam.put("conditions", conditions);
        return queryParam;
    }

    public static String getResOut(String baseUrl, String apikey, String param) {
        String queryUrl = String.format(RES_QUERY_URL, baseUrl, apikey);
        return HttpUtil.doRequestPost(queryUrl, param);
    }

    public static String commitDiy(String BASE_URL, String API_KEY, String param) {
        String cmdbUrl = String.format(DIY_COMMIT_URL, BASE_URL, API_KEY);
        return HttpUtil.doRequestPost(cmdbUrl, param);
    }

    @Override
    public void pushCheckReceiveProcess(List<CheckReceive> checkReceives) {
        String oaToken = getOaToken();
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("Content-Type", "application/json");
        headerMap.put("auth_token", oaToken);
        String requestBody = JSON.toJSONString(checkReceives);
        // 推送到货验收数据到oa
        HttpUtil.doRequestPost(sceneDiyProperty.getOaUrl() + "/operation/erp/cmdb/acceptanceProcessDataSyn", requestBody, headerMap);
    }

}
