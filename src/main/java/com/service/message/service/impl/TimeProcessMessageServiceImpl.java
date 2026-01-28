package com.service.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.service.message.pojo.*;
import com.service.message.property.SceneDiyProperty;
import com.service.message.service.TimeProcessMessageService;
import com.service.message.utils.HttpUtil;
import com.service.message.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/21 15:32
 * @Description: 定时流程消息接口实现类
 */
@Service
@Slf4j
public class TimeProcessMessageServiceImpl implements TimeProcessMessageService {
    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    @Override
    public void triggerUpdateNumProcess() {
        // 备品备件补充数量流程只查询公有云的预留
        CmdbModelParams.QueryCondition queryCondition = new CmdbModelParams.QueryCondition();
        queryCondition.setField("asset_purpose");
        queryCondition.setValue("公有云备品备件预留");
        CmdbModelParams.QueryCondition queryCondition1 = new CmdbModelParams.QueryCondition();
        queryCondition1.setField("reserve_source");
        queryCondition1.setValue("备品备件预留");
        List<CmdbModelParams.QueryCondition> reserveConditions = new ArrayList<>();
        reserveConditions.add(queryCondition);
        reserveConditions.add(queryCondition1);
        // 所有设备/配件的公有云预留需求
        List<AssetReserve> assetReserveList = queryModelData("assets_reserve",
                Arrays.asList("id", "classCode", "name", "asset_type", "asset_purpose", "demando_count"),
                AssetReserve.class, reserveConditions);

        Set<String> reservedIds = assetReserveList.stream().map(AssetReserve::getId).collect(Collectors.toSet());
        CmdbModelParams.QueryCondition assetQueryCondition = new CmdbModelParams.QueryCondition();
        assetQueryCondition.setField("link_assets_reserve");
        assetQueryCondition.setOperator("IN");
        assetQueryCondition.setValue(reservedIds);
        List<CmdbModelParams.QueryCondition> reserveDetailConditions = new ArrayList<>();
        reserveDetailConditions.add(assetQueryCondition);

        List<AssetsReserveDetail> assetsReserveDetailList = queryModelData("assets_reserve_detail",
                Arrays.asList("id", "classCode", "name", "asset_purpose", "asset_type", "main_device_type", "accessory_type",
                        "Manufacturer", "manufacturer_model", "host_type", "demando_count", "reserved_user", "link_assets_reserve"),
                AssetsReserveDetail.class, reserveDetailConditions);
        // 设备的公有云预留需求明细
        List<AssetsReserveDetail> physicalReserveDetails = assetsReserveDetailList.stream()
                .filter(assetReserve -> "设备".equals(assetReserve.getAsset_type())).collect(Collectors.toList());
        // 配件的公有云预留需求明细
        List<AssetsReserveDetail> accessoryReserveDetails = assetsReserveDetailList.stream()
                .filter(assetReserve -> "配件".equals(assetReserve.getAsset_type())).collect(Collectors.toList());


        Set<String> physicalAssetIds = assetReserveList.stream()
                .filter(assetReserve -> "设备".equals(assetReserve.getAsset_type()))
                .map(AssetReserve::getId)
                .collect(Collectors.toSet());
        List<CmdbModelParams.QueryCondition> reservedConditions1 = generateReservedConditions(physicalAssetIds);
        // 资产状态为已预留并且关联预留需求的设备
        List<PhysicalAsset> physicalAssets = queryModelData("physical_assets",
                Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "main_device_type", "reserved_user",
                        "asset_status", "asset_category", "ownership_entity", "host_type", "reserv_borrow_expiry_date", "reserve_ref"),
                PhysicalAsset.class, reservedConditions1);

        Set<String> accessoryAssetIds = assetReserveList.stream()
                .filter(assetReserve -> "配件".equals(assetReserve.getAsset_type()))
                .map(AssetReserve::getId)
                .collect(Collectors.toSet());
        List<CmdbModelParams.QueryCondition> reservedConditions2 = generateReservedConditions(accessoryAssetIds);
        // 资产状态为已预留并且关联预留需求的配件
        List<AccessoryAsset> accessoryAssets = queryModelData("accessory_asset",
                Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "accessory_type", "reserved_user",
                        "asset_status", "asset_category", "ownership_entity", "reserv_borrow_expiry_date", "reserve_ref"),
                AccessoryAsset.class, reservedConditions2);

        List<ConsumablesReserve> consumablesReserves = queryModelData("consumables_reserve",
                Arrays.asList("id", "classCode", "name", "consumable_code", "asset_purpose", "demando_count",
                        "reserved_user", "reserv_borrow_expiry_date", "reserved_num", "reserved_chg"),
                ConsumablesReserve.class, reserveConditions);

        // 预留人列表
        Set<String> reserved_user = new HashSet<>();
        // 设备资产列表
        List<Map<String, Object>> device_list_1 = comparePhysicalVsReserveDemand(physicalAssets, physicalReserveDetails, reserved_user);
        // 配件资产列表
        List<Map<String, Object>> accessory_list_1 = compareAccessoryVsReserveDemand(accessoryAssets, accessoryReserveDetails, reserved_user);
        // 耗材资产列表
        List<Map<String, Object>> consumable_list_1 = new ArrayList<>();
        Map<String, ConsumablesReserve> consumablesReserveMap = groupAndSumByConsumableType(consumablesReserves, reserved_user);
        for (Map.Entry<String, ConsumablesReserve> entry : consumablesReserveMap.entrySet()) {
            ConsumablesReserve consumablesReserve = entry.getValue();
            Integer demando_count = Optional.ofNullable(consumablesReserve.getDemando_count()).orElse(0);
            Integer reserved_num = Optional.ofNullable(consumablesReserve.getReserved_num()).orElse(0);
            if (reserved_num < demando_count) {
                // 构建耗材明细
                Map<String, Object> consumableMap = new HashMap<>();
                List<String> spec_type = new ArrayList<>();
                spec_type.add(entry.getKey());
                consumableMap.put("spec_type", spec_type);
                consumableMap.put("number", consumablesReserve.getDemando_count());
                consumableMap.put("reserved_num3", consumablesReserve.getReserved_num());
                consumableMap.put("applicant", consumablesReserve.getApplicant());
                consumable_list_1.add(consumableMap);
            }
        }


        // 需要处理的资产类型列表(为空则直接返回)
        List<String> select_type = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(device_list_1)) select_type.add("设备");
        if (CollectionUtils.isNotEmpty(accessory_list_1)) select_type.add("配件");
        if (CollectionUtils.isNotEmpty(consumable_list_1)) select_type.add("耗材");
        if (CollectionUtils.isEmpty(select_type)) return;

        // 组装数据并发起流程
        Map<String, Object> diyMap = new HashMap<>();
        diyMap.put("catalog_id", "50");
        diyMap.put("service_id", "106");
        Map<String, Object> field_dict = new HashMap<>();
        field_dict.put("title", "备品备件预留（补充数量）");
        List<String> purpose = new ArrayList<>();
        purpose.add("{\"__id\":\"公有云备品备件预留\",\"__name\":\"公有云备品备件预留\"}");
        field_dict.put("purpose", purpose);
        field_dict.put("select_type", select_type);
        field_dict.put("reserved_user", reserved_user);
        field_dict.put("device_list_1", device_list_1);
        field_dict.put("accessory_list_1", accessory_list_1);
        field_dict.put("consumable_list_1", consumable_list_1);
        diyMap.put("field_dict", field_dict);
        HttpUtil.doRequestPost(
                String.format("%s/scenediy/open/ticket/ticket/create_ticket_and_auto_proceed?apikey=%s", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
                JSON.toJSONString(diyMap));
    }

    @Override
    public void triggerReservedDelayProcess(Integer day) {
        // 查询来自集群建设设备预留流程的预留
        CmdbModelParams.QueryCondition queryCondition = new CmdbModelParams.QueryCondition();
        queryCondition.setField("reserve_source");
        queryCondition.setValue("集群建设设备预留");
        List<CmdbModelParams.QueryCondition> reserveConditions = new ArrayList<>();
        reserveConditions.add(queryCondition);
        // 所有设备/配件的预留需求
        List<AssetReserve> assetReserveList = queryModelData("assets_reserve",
                Arrays.asList("id", "classCode", "name", "asset_type", "asset_purpose", "demando_count"),
                AssetReserve.class, reserveConditions);
        // 所有设备的预留需求并按用途分组
        Map<String, List<AssetReserve>> physicalAssetReserveMap = Optional.ofNullable(assetReserveList).orElse(new ArrayList<>())
                .stream().filter(assetReserve -> "设备".equals(assetReserve.getAsset_type()))
                .collect(Collectors.groupingBy(AssetReserve::getAsset_purpose, Collectors.toList()));
        // 所有配件的预留需求并按用途分组
        Map<String, List<AssetReserve>> accessoryAssetReserveMap = Optional.ofNullable(assetReserveList).orElse(new ArrayList<>())
                .stream().filter(assetReserve -> "配件".equals(assetReserve.getAsset_type()))
                .collect(Collectors.groupingBy(AssetReserve::getAsset_purpose, Collectors.toList()));

        // 日期map
        Map<String, String> dateMap = calculateBeforeAndAfterDate(day);

        if (MapUtils.isEmpty(dateMap)) {
            // 自动释放预留
            autoReleaseReserved(assetReserveList, physicalAssetReserveMap, accessoryAssetReserveMap);
            return;
        }

        List<String> expiryDateList = new ArrayList<>();
        expiryDateList.add(dateMap.get("beforeDate") + " 00:00:00");
        expiryDateList.add(dateMap.get("afterDate") + " 23:59:59");
        CmdbModelParams.QueryCondition assetQueryCondition3 = new CmdbModelParams.QueryCondition();
        assetQueryCondition3.setField("reserv_borrow_expiry_date");
        assetQueryCondition3.setOperator("RANGE");
        assetQueryCondition3.setValue(expiryDateList);
        reserveConditions.add(assetQueryCondition3);

        // 所有耗材的预留需求并按用途分组
        Map<String, List<ConsumablesReserve>> consumablesReserveMap = Optional.ofNullable(queryModelData("consumables_reserve",
                        Arrays.asList("id", "classCode", "name", "consumable_code", "asset_purpose", "demando_count",
                                "reserved_user", "reserv_borrow_expiry_date", "reserved_num", "reserved_chg"),
                        ConsumablesReserve.class, reserveConditions))
                .orElse(new ArrayList<>())
                .stream()
                .collect(Collectors.groupingBy(ConsumablesReserve::getAsset_purpose, Collectors.toList()));

        // 所有用途Set集合
        Set<String> assetPurposeSet = new HashSet<>();
        assetPurposeSet.addAll(physicalAssetReserveMap.keySet());
        assetPurposeSet.addAll(accessoryAssetReserveMap.keySet());
        assetPurposeSet.addAll(consumablesReserveMap.keySet());

        // 当前已预留的所有设备资产
        Map<String, List<PhysicalAsset>> physicalAssetMap = new HashMap<>();
        if (MapUtils.isNotEmpty(physicalAssetReserveMap)) {
            Set<String> ids = assetReserveList.stream()
                    .filter(assetReserve -> "设备".equals(assetReserve.getAsset_type()))
                    .map(AssetReserve::getId)
                    .collect(Collectors.toSet());
            List<CmdbModelParams.QueryCondition> queryConditions = generateReservedConditions(ids, dateMap.get("beforeDate"), dateMap.get("afterDate"));
            // 资产状态为已预留并且关联预留需求的设备
            List<PhysicalAsset> physicalAssets = queryModelData("physical_assets",
                    Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "main_device_type", "reserved_user",
                            "asset_status", "asset_category", "ownership_entity", "host_type", "reserv_borrow_expiry_date", "reserve_ref", "reserved_chg"),
                    PhysicalAsset.class, queryConditions);
            physicalAssetMap = physicalAssets.stream().filter(asset -> asset.getReserve_ref() != null)
                    .collect(Collectors.groupingBy(asset -> asset.getReserve_ref().getName(), Collectors.toList()));
        }

        // 当前已预留的所有配件资产并按用途分类
        Map<String, List<AccessoryAsset>> accessoryAssetMap = new HashMap<>();
        if (MapUtils.isNotEmpty(accessoryAssetReserveMap)) {
            Set<String> ids = assetReserveList.stream()
                    .filter(assetReserve -> "配件".equals(assetReserve.getAsset_type()))
                    .map(AssetReserve::getId)
                    .collect(Collectors.toSet());
            List<CmdbModelParams.QueryCondition> queryConditions = generateReservedConditions(ids, dateMap.get("beforeDate"), dateMap.get("afterDate"));
            // 资产状态为已预留并且关联预留需求的配件
            List<AccessoryAsset> accessoryAssets = queryModelData("accessory_asset",
                    Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "accessory_type", "reserved_user",
                            "asset_status", "asset_category", "ownership_entity", "reserv_borrow_expiry_date", "reserve_ref", "reserved_chg"),
                    AccessoryAsset.class, queryConditions);
            accessoryAssetMap = accessoryAssets.stream().filter(asset -> asset.getReserve_ref() != null)
                    .collect(Collectors.groupingBy(asset -> asset.getReserve_ref().getName(), Collectors.toList()));
        }


        // 没有需要处理的用途
        if (CollectionUtils.isEmpty(assetPurposeSet)) return;

        // 设备类型字典
        Map<String, String> deviceTypeCode = getDictMap("AM_device_type_code");
        // 资产分类字典
        Map<String, String> categoryCode = getDictMap("AM_category_code");

        for (String purpose : assetPurposeSet) {
            // 预留人列表
            Set<String> reserved_user = new HashSet<>();
            // 设备资产列表
            List<Map<String, Object>> physical_assets = new ArrayList<>();
            // 配件资产列表
            List<Map<String, Object>> accessory_asset = new ArrayList<>();
            // 耗材资产列表
            List<Map<String, Object>> consumables = new ArrayList<>();

            // 构建设备明细表参数
            List<PhysicalAsset> physicalAssets = physicalAssetMap.get(purpose + "/设备");
            if (CollectionUtils.isNotEmpty(physicalAssets)) {
                for (PhysicalAsset physicalAsset : physicalAssets) {
                    // 添加预留人
                    Set<String> accountSet = Optional.ofNullable(physicalAsset.getReserved_user())
                            .orElse(new ArrayList<>())
                            .stream()
                            .filter(Objects::nonNull)
                            .map(ReservedUser::getAccount)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    reserved_user.addAll(accountSet);
                    Map<String, Object> physicalMap = new HashMap<>();
                    List<String> device_sn = new ArrayList<>();
                    device_sn.add(String.format("{\"__id\":\"%s\",\"__name\":\"%s\"}", physicalAsset.getId(), physicalAsset.getSn()));
                    physicalMap.put("device_sn", device_sn);
                    physicalMap.put("device_type1", deviceTypeCode.get(physicalAsset.getMain_device_type()));
                    physicalMap.put("model_name1", physicalAsset.getHost_type());
                    physicalMap.put("vendor1", Optional.ofNullable(physicalAsset.getManufacturer()).orElse(new BaseObject()).getName());
                    physicalMap.put("vendor_model1", Optional.ofNullable(physicalAsset.getManufactModel()).orElse(new BaseObject()).getName());
                    physicalMap.put("applicant1", String.join(";", accountSet));
                    physicalMap.put("reserved_date1", physicalAsset.getReserv_borrow_expiry_date());
                    physicalMap.put("category1", categoryCode.get(physicalAsset.getAsset_category()));
                    physical_assets.add(physicalMap);
                }
            }

            // 构建配件明细表参数
            List<AccessoryAsset> accessoryAssets = accessoryAssetMap.get(purpose + "/配件");
            if (CollectionUtils.isNotEmpty(accessoryAssets)) {
                for (AccessoryAsset accessoryAsset : accessoryAssets) {
                    // 添加预留人
                    Set<String> accountSet = Optional.ofNullable(accessoryAsset.getReserved_user())
                            .orElse(new ArrayList<>())
                            .stream()
                            .filter(Objects::nonNull)
                            .map(ReservedUser::getAccount)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    reserved_user.addAll(accountSet);
                    Map<String, Object> accessoryMap = new HashMap<>();
                    List<String> accessory_sn = new ArrayList<>();
                    accessory_sn.add(String.format("{\"__id\":\"%s\",\"__name\":\"%s\"}", accessoryAsset.getId(), accessoryAsset.getSn()));
                    accessoryMap.put("accessory_sn", accessory_sn);
                    accessoryMap.put("accessory_type2", accessoryAsset.getAccessory_type());
                    accessoryMap.put("vendor2", Optional.ofNullable(accessoryAsset.getManufacturer()).orElse(new BaseObject()).getName());
                    accessoryMap.put("vendor_model2", Optional.ofNullable(accessoryAsset.getManufactModel()).orElse(new BaseObject()).getName());
                    accessoryMap.put("applicant2", String.join(";", accountSet));
                    accessoryMap.put("reserved_date2", accessoryAsset.getReserv_borrow_expiry_date());
                    accessory_asset.add(accessoryMap);
                }
            }

            // 构建耗材明细表参数
            List<ConsumablesReserve> consumablesReserves = consumablesReserveMap.get(purpose);
            if (CollectionUtils.isNotEmpty(consumablesReserves)) {
                for (ConsumablesReserve consumablesReserve : consumablesReserves) {
                    // 添加预留人
                    Set<String> accountSet = Optional.ofNullable(consumablesReserve.getReserved_user())
                            .orElse(new ArrayList<>())
                            .stream()
                            .filter(Objects::nonNull)
                            .map(ReservedUser::getAccount)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    reserved_user.addAll(accountSet);
                    Map<String, Object> consumableMap = new HashMap<>();
                    List<String> consumable_name = new ArrayList<>();
                    BaseObject baseObject = Optional.ofNullable(consumablesReserve.getConsumable_code()).orElse(new BaseObject());
                    consumable_name.add(String.format("{\"consumable_code\":{\"name\":\"%s\",\"id\":\"%s\"},\"__id\":\"%s\",\"__name\":\"%s\"}",
                            baseObject.getName(), baseObject.getId(), consumablesReserve.getId(), consumablesReserve.getName()));
                    consumableMap.put("consumable_name", consumable_name);
                    String name = baseObject.getName();
                    consumableMap.put("consumable_type", name.lastIndexOf("/") != -1 ? name.substring(0, name.lastIndexOf("/")) : name);
                    consumableMap.put("reserved_num", consumablesReserve.getReserved_num());
                    consumableMap.put("applicant3", String.join(";", accountSet));
                    consumableMap.put("reserved_date3", consumablesReserve.getReserv_borrow_expiry_date());
                    consumables.add(consumableMap);
                }
            }

            // 需要处理的资产类型列表(为空则跳过)
            List<String> asset_type = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(physical_assets)) asset_type.add("设备");
            if (CollectionUtils.isNotEmpty(accessory_asset)) asset_type.add("配件");
            if (CollectionUtils.isNotEmpty(consumables)) asset_type.add("耗材");
            if (CollectionUtils.isEmpty(asset_type)) continue;

            // 组装数据并发起流程
            Map<String, Object> diyMap = new HashMap<>();
            diyMap.put("catalog_id", "50");
            diyMap.put("service_id", "100");
            Map<String, Object> field_dict = new HashMap<>();
            field_dict.put("title", "设备预留延期");
            List<String> purposeList = new ArrayList<>();
            purposeList.add(String.format("{\"__id\":\"%s\",\"__name\":\"%s\"}", purpose, purpose));
            field_dict.put("purpose", purposeList);
            field_dict.put("asset_type", asset_type);
            field_dict.put("reserved_user", reserved_user);
            field_dict.put("physical_assets", physical_assets);
            field_dict.put("accessory_asset", accessory_asset);
            field_dict.put("consumables", consumables);
            diyMap.put("field_dict", field_dict);
            HttpUtil.doRequestPost(
                    String.format("%s/scenediy/open/ticket/ticket/create_ticket_and_auto_proceed?apikey=%s", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
                    JSON.toJSONString(diyMap));
        }

        // 自动释放预留
        autoReleaseReserved(assetReserveList, physicalAssetReserveMap, accessoryAssetReserveMap);
    }

    /**
     * 自动释放预留
     *
     * @param assetReserveList
     * @param physicalAssetReserveMap
     * @param accessoryAssetReserveMap
     */
    public void autoReleaseReserved(List<AssetReserve> assetReserveList, Map<String, List<AssetReserve>> physicalAssetReserveMap, Map<String, List<AssetReserve>> accessoryAssetReserveMap) {
        // 获取七天前的日期
        String expiryDate = LocalDate.now().minusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        CmdbModelParams.QueryCondition queryCondition = new CmdbModelParams.QueryCondition();
        queryCondition.setField("reserve_source");
        queryCondition.setValue("集群建设设备预留");
        // 查询条件:日期
        List<String> expiryDateList = new ArrayList<>();
        expiryDateList.add(expiryDate + " 00:00:00");
        expiryDateList.add(expiryDate + " 23:59:59");
        CmdbModelParams.QueryCondition queryCondition3 = new CmdbModelParams.QueryCondition();
        queryCondition3.setField("reserv_borrow_expiry_date");
        queryCondition3.setOperator("RANGE");
        queryCondition3.setValue(expiryDateList);
//        CmdbModelParams.QueryCondition queryCondition4 = new CmdbModelParams.QueryCondition();
//        queryCondition4.setField("reserved_chg");
//        queryCondition4.setValue("false");
        List<CmdbModelParams.QueryCondition> reserveConditions = new ArrayList<>();
        reserveConditions.add(queryCondition);
        reserveConditions.add(queryCondition3);
//        reserveConditions.add(queryCondition4);

        // 所有需要释放的耗材预留需求并按用途分组
        List<ConsumablesReserve> consumablesReserveList = queryModelData("consumables_reserve",
                Arrays.asList("id", "classCode", "name", "consumable_code", "asset_purpose", "demando_count",
                        "reserved_user", "reserv_borrow_expiry_date", "reserved_num", "reserved_chg"),
                ConsumablesReserve.class, reserveConditions);
        Map<String, List<ConsumablesReserve>> consumablesReserveMap = Optional.ofNullable(consumablesReserveList)
                .orElse(new ArrayList<>())
                .stream()
                .collect(Collectors.groupingBy(ConsumablesReserve::getAsset_purpose, Collectors.toList()));


        // 所有用途Set集合
        Set<String> assetPurposeSet = new HashSet<>();
        assetPurposeSet.addAll(physicalAssetReserveMap.keySet());
        assetPurposeSet.addAll(accessoryAssetReserveMap.keySet());
        assetPurposeSet.addAll(consumablesReserveMap.keySet());

        // 当前已预留的所有设备资产与应该释放的设备资产
        Map<String, List<PhysicalAsset>> physicalAssetMap = new HashMap<>();
        Map<String, List<PhysicalAsset>> releasePhysicalAssetMap = new HashMap<>();
        if (MapUtils.isNotEmpty(physicalAssetReserveMap)) {
            Set<String> ids = assetReserveList.stream()
                    .filter(assetReserve -> "设备".equals(assetReserve.getAsset_type()))
                    .map(AssetReserve::getId)
                    .collect(Collectors.toSet());

            List<CmdbModelParams.QueryCondition> queryConditions = generateReservedConditions(ids);
            // 资产状态为已预留并且关联预留需求的设备
            List<PhysicalAsset> physicalAssets = queryModelData("physical_assets",
                    Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "main_device_type", "reserved_user",
                            "asset_status", "asset_category", "ownership_entity", "host_type", "reserv_borrow_expiry_date", "reserve_ref", "reserved_chg"),
                    PhysicalAsset.class, queryConditions);
            physicalAssetMap = physicalAssets.stream().filter(asset -> asset.getReserve_ref() != null)
                    .collect(Collectors.groupingBy(asset -> asset.getReserve_ref().getName(), Collectors.toList()));

            List<CmdbModelParams.QueryCondition> queryConditions1 = generateReleaseReservedConditions(ids, expiryDate);
            // 到期自动释放的设备资产
            List<PhysicalAsset> releasePhysicalAssets = queryModelData("physical_assets",
                    Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "main_device_type", "reserved_user",
                            "asset_status", "asset_category", "ownership_entity", "host_type", "reserv_borrow_expiry_date", "reserve_ref", "reserved_chg"),
                    PhysicalAsset.class, queryConditions1);
            releasePhysicalAssetMap = releasePhysicalAssets.stream().filter(asset -> asset.getReserve_ref() != null)
                    .collect(Collectors.groupingBy(asset -> asset.getReserve_ref().getName(), Collectors.toList()));
        }

        // 当前已预留的所有配件资产并按用途分类
        Map<String, List<AccessoryAsset>> accessoryAssetMap = new HashMap<>();
        Map<String, List<AccessoryAsset>> releaseAccessoryAssetMap = new HashMap<>();
        if (MapUtils.isNotEmpty(accessoryAssetReserveMap)) {
            Set<String> ids = assetReserveList.stream()
                    .filter(assetReserve -> "配件".equals(assetReserve.getAsset_type()))
                    .map(AssetReserve::getId)
                    .collect(Collectors.toSet());

            List<CmdbModelParams.QueryCondition> queryConditions = generateReservedConditions(ids);
            // 资产状态为已预留并且关联预留需求的配件
            List<AccessoryAsset> accessoryAssets = queryModelData("accessory_asset",
                    Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "accessory_type", "reserved_user",
                            "asset_status", "asset_category", "ownership_entity", "reserv_borrow_expiry_date", "reserve_ref", "reserved_chg"),
                    AccessoryAsset.class, queryConditions);
            accessoryAssetMap = accessoryAssets.stream().filter(asset -> asset.getReserve_ref() != null)
                    .collect(Collectors.groupingBy(asset -> asset.getReserve_ref().getName(), Collectors.toList()));

            List<CmdbModelParams.QueryCondition> queryConditions1 = generateReleaseReservedConditions(ids, expiryDate);
            // 到期自动释放的配件资产
            List<AccessoryAsset> releaseAccessoryAssets = queryModelData("accessory_asset",
                    Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "accessory_type", "reserved_user",
                            "asset_status", "asset_category", "ownership_entity", "reserv_borrow_expiry_date", "reserve_ref", "reserved_chg"),
                    AccessoryAsset.class, queryConditions1);
            releaseAccessoryAssetMap = releaseAccessoryAssets.stream().filter(asset -> asset.getReserve_ref() != null)
                    .collect(Collectors.groupingBy(asset -> asset.getReserve_ref().getName(), Collectors.toList()));
        }

        // 待更新资产数据(设备、配件、耗材)
        List<Map<String, Object>> needUpdateAssetList = new ArrayList<>();
        // 预留需求表待删除ID集合(设备、配件、耗材)
        Set<String> needDeleteIds = new HashSet<>();

        Set<String> consumablesIds = Optional.ofNullable(consumablesReserveList).orElse(new ArrayList<>()).stream()
                .filter(consumablesReserve -> consumablesReserve.getConsumable_code() != null)
                .map(consumablesReserve -> consumablesReserve.getConsumable_code().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ConsumablesAsset> consumablesAssets = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(consumablesIds)) {
            CmdbModelParams.QueryCondition consumablesQueryCondition = new CmdbModelParams.QueryCondition();
            consumablesQueryCondition.setField("id");
            consumablesQueryCondition.setOperator("IN");
            consumablesQueryCondition.setValue(consumablesIds);
            List<CmdbModelParams.QueryCondition> consumablesConditions = new ArrayList<>();
            consumablesConditions.add(consumablesQueryCondition);

            consumablesAssets = queryModelData("consumables",
                    Arrays.asList("id", "classCode", "store_num", "usage_num", "reserved_num"),
                    ConsumablesAsset.class, consumablesConditions);
        }

        Map<String, ConsumablesAsset> consumablesAssetMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(consumablesAssets)) {
            // 耗材资产
            consumablesAssetMap = consumablesAssets.stream()
                    .collect(Collectors.toMap(
                            // Key：取对象的id
                            ConsumablesAsset::getId,
                            // Value：直接用对象本身
                            consumable -> consumable,
                            // 冲突解决：同一个id有多个对象时，保留第一个（可改为newVal保留最后一个）
                            (existingAsset, newAsset) -> existingAsset
                    ));
        }


        for (String purpose : assetPurposeSet) {
            // 1. 处理设备预留
            List<PhysicalAsset> physicalAssets = physicalAssetMap.get(purpose + "/设备");
            List<PhysicalAsset> releasePhysicalAssets = releasePhysicalAssetMap.get(purpose + "/设备");
            if (CollectionUtils.isNotEmpty(physicalAssets) && CollectionUtils.isNotEmpty(releasePhysicalAssets)) {
                // 自动释放后清空预留时间、清空预留人、关联预留需求字段,资产状态改为库存空闲
                for (PhysicalAsset releasePhysicalAsset : releasePhysicalAssets) {
                    Map<String, Object> diyMap = new HashMap<>();
                    diyMap.put("id", releasePhysicalAsset.getId());
                    diyMap.put("classCode", "physical_assets");
                    diyMap.put("asset_status", "idle");
                    diyMap.put("reserv_borrow_expiry_date", "");
                    diyMap.put("reserved_user", "");
                    diyMap.put("reserve_ref", "");
                    needUpdateAssetList.add(diyMap);
                }

                // 判断预留需求全部释放后删除预留需求表数据
                if (physicalAssets.size() == releasePhysicalAssets.size()) {
                    needDeleteIds.addAll(Optional.ofNullable(physicalAssetReserveMap.get(purpose))
                            .orElse(new ArrayList<>())
                            .stream()
                            .filter(Objects::nonNull)
                            .map(AssetReserve::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }
            }

            // 2. 处理配件预留
            List<AccessoryAsset> accessoryAssets = accessoryAssetMap.get(purpose + "/配件");
            List<AccessoryAsset> releaseAccessoryAssets = releaseAccessoryAssetMap.get(purpose + "/配件");
            if (CollectionUtils.isNotEmpty(accessoryAssets) && CollectionUtils.isNotEmpty(releaseAccessoryAssets)) {
                // 自动释放后清空预留时间、清空预留人、关联预留需求字段,资产状态改为库存空闲
                for (AccessoryAsset releaseAccessoryAsset : releaseAccessoryAssets) {
                    Map<String, Object> diyMap = new HashMap<>();
                    diyMap.put("id", releaseAccessoryAsset.getId());
                    diyMap.put("classCode", "accessory_asset");
                    diyMap.put("asset_status", "idle");
                    diyMap.put("reserv_borrow_expiry_date", "");
                    diyMap.put("reserved_user", "");
                    diyMap.put("reserve_ref", "");
                    needUpdateAssetList.add(diyMap);
                }
                // 判断预留需求全部释放后删除预留需求表数据
                if (accessoryAssets.size() == releaseAccessoryAssets.size()) {
                    needDeleteIds.addAll(Optional.ofNullable(accessoryAssetReserveMap.get(purpose))
                            .orElse(new ArrayList<>())
                            .stream()
                            .filter(Objects::nonNull)
                            .map(AssetReserve::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }
            }

            // 3. 处理耗材预留
            List<ConsumablesReserve> consumablesReserves = consumablesReserveMap.get(purpose);
            if (CollectionUtils.isNotEmpty(consumablesReserves)) {
                // 自动释放后可用数量增加,库存数量增加,预留数量减少
                for (ConsumablesReserve consumablesReserve : consumablesReserves) {
                    String id = Optional.ofNullable(consumablesReserve.getConsumable_code()).orElse(new BaseObject()).getId();
                    ConsumablesAsset consumablesAsset = consumablesAssetMap.get(id);
                    // 存在此耗材
                    if (consumablesAsset != null) {
                        Integer reservedNum = Optional.ofNullable(consumablesReserve.getReserved_num()).orElse(0);
                        Map<String, Object> diyMap = new HashMap<>();
                        diyMap.put("id", id);
                        diyMap.put("classCode", "consumables");
                        diyMap.put("store_num", Optional.ofNullable(consumablesAsset.getStore_num()).orElse(0) + reservedNum);
                        diyMap.put("usage_num", Optional.ofNullable(consumablesAsset.getUsage_num()).orElse(0) + reservedNum);
                        diyMap.put("reserved_num", Optional.ofNullable(consumablesAsset.getReserved_num()).orElse(0) - reservedNum);
                        needUpdateAssetList.add(diyMap);
                    }
                }

                // 删除预留需求表数据
                needDeleteIds.addAll(Optional.ofNullable(consumablesReserves)
                        .orElse(new ArrayList<>())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(ConsumablesReserve::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
            }

        }

        // 删除预留需求表数据(设备、配件、耗材)
        if (CollectionUtils.isNotEmpty(needDeleteIds)) {
            HttpUtil.doRequestPost(
                    String.format("%s/store/openapi/v2/resources/delete_by_id?apikey=%s&source=sap", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
                    JSON.toJSONString(needDeleteIds));
        }

        // 更新资产数据(设备、配件、耗材)
        if (CollectionUtils.isNotEmpty(needUpdateAssetList)) {
            HttpUtil.doRequestPost(
                    String.format("%s/store/openapi/v2/resources/batch_save?apikey=%s&source=sap", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
                    JSON.toJSONString(needUpdateAssetList));
        }


    }

    @Override
    public void triggerOthersExpireProcess() {
        // 1. 获取今天的日期
        LocalDate today = LocalDate.now();
        // 定义日期格式（yyyy-MM-dd）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 2. 分别向后推算1、3、6个月
        LocalDate before1Month = today.plusMonths(1);
        LocalDate before3Months = today.plusMonths(3);
        LocalDate before6Months = today.plusMonths(6);

        // 3. 格式化为目标字符串并输出
        String date1 = before1Month.format(formatter);
        triggerOthersExpireProcess(date1);
        String date3 = before3Months.format(formatter);
        triggerOthersExpireProcess(date3);
        String date6 = before6Months.format(formatter);
        triggerOthersExpireProcess(date6);
    }

    /**
     * 根据到期时间触发设备(他方物权)到期提醒流程
     *
     * @param expiryDate
     */
    public void triggerOthersExpireProcess(String expiryDate) {
        // 查询条件:资产分类是二类、三类
        List<String> assetCategory = new ArrayList<>();
        assetCategory.add("IIEquipment");
        assetCategory.add("IIIvenEquipment");
        CmdbModelParams.QueryCondition assetQueryCondition1 = new CmdbModelParams.QueryCondition();
        assetQueryCondition1.setField("asset_category");
        assetQueryCondition1.setOperator("IN");
        assetQueryCondition1.setValue(assetCategory);

        // 查询条件:物权归属方包含二类、三类
        List<String> ownershipEntity = new ArrayList<>();
        ownershipEntity.add("fj01");
        ownershipEntity.add("fj02");
        ownershipEntity.add("my");
        ownershipEntity.add("tj01");
        ownershipEntity.add("yuflc");
        ownershipEntity.add("yq");
        ownershipEntity.add("cqrcfl");
        ownershipEntity.add("ucloud");
        ownershipEntity.add("west_hpc");
        ownershipEntity.add("szqx");
        CmdbModelParams.QueryCondition assetQueryCondition2 = new CmdbModelParams.QueryCondition();
        assetQueryCondition2.setField("ownership_entity");
        assetQueryCondition2.setOperator("IN");
        assetQueryCondition2.setValue(ownershipEntity);

        // 查询条件:日期
        List<String> expiryDateList = new ArrayList<>();
        expiryDateList.add(expiryDate + " 00:00:00");
        expiryDateList.add(expiryDate + " 23:59:59");
        CmdbModelParams.QueryCondition assetQueryCondition3 = new CmdbModelParams.QueryCondition();
        assetQueryCondition3.setField("reserv_borrow_expiry_date");
        assetQueryCondition3.setOperator("RANGE");
        assetQueryCondition3.setValue(expiryDateList);

        List<CmdbModelParams.QueryCondition> queryConditions = new ArrayList<>();
        queryConditions.add(assetQueryCondition1);
        queryConditions.add(assetQueryCondition2);
        queryConditions.add(assetQueryCondition3);


        List<PhysicalAsset> physicalAssets = queryModelData("physical_assets",
                Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "main_device_type",
                        "asset_status", "asset_category", "ownership_entity", "host_type", "reserv_borrow_expiry_date", "reserve_ref"),
                PhysicalAsset.class, queryConditions);

        List<AccessoryAsset> accessoryAssets = queryModelData("accessory_asset",
                Arrays.asList("id", "classCode", "name", "sn", "Manufacturer", "ManufactModel", "accessory_type",
                        "asset_status", "asset_category", "ownership_entity", "reserv_borrow_expiry_date", "reserve_ref"),
                AccessoryAsset.class, queryConditions);


        // 设备资产列表
        List<Map<String, List<String>>> asset_list = new ArrayList<>();
        // 配件资产列表
        List<Map<String, List<String>>> accessory_list = new ArrayList<>();
        // 构建设备明细参数
        if (CollectionUtils.isNotEmpty(physicalAssets)) {
            for (PhysicalAsset physicalAsset : physicalAssets) {
                Map<String, List<String>> map = new HashMap<>();
                List<String> asset_sn = new ArrayList<>();
                asset_sn.add(String.format("{\"__id\":\"%s\",\"__name\":\"%s\"}", physicalAsset.getId(), physicalAsset.getSn()));
                map.put("asset_sn", asset_sn);
                asset_list.add(map);
            }
        }
        // 构建配件明细参数
        if (CollectionUtils.isNotEmpty(accessoryAssets)) {
            for (AccessoryAsset accessoryAsset : accessoryAssets) {
                Map<String, List<String>> map = new HashMap<>();
                List<String> accessory_sn = new ArrayList<>();
                accessory_sn.add(String.format("{\"__id\":\"%s\",\"__name\":\"%s\"}", accessoryAsset.getId(), accessoryAsset.getSn()));
                map.put("accessory_sn", accessory_sn);
                accessory_list.add(map);
            }
        }

        // 需要处理的资产类型列表(为空则跳过)
        List<String> asset_type_1 = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(asset_list)) asset_type_1.add("asset");
        if (CollectionUtils.isNotEmpty(accessory_list)) asset_type_1.add("accessory");
        if (CollectionUtils.isEmpty(asset_type_1)) return;

        // 组装数据并发起流程
        Map<String, Object> diyMap = new HashMap<>();
        diyMap.put("catalog_id", "36");
        diyMap.put("service_id", "82");
        Map<String, Object> field_dict = new HashMap<>();
        field_dict.put("title", "设备（他方物权）到期提醒");
        field_dict.put("asset_type_1", asset_type_1);
        field_dict.put("asset_list", asset_list);
        field_dict.put("accessory_list", accessory_list);
        diyMap.put("field_dict", field_dict);
        HttpUtil.doRequestPost(
                String.format("%s/scenediy/open/ticket/ticket/create_ticket_and_auto_proceed?apikey=%s", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
                JSON.toJSONString(diyMap));

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
                    String.format("%s/store/openapi/v2/resources/query?apikey=%s&source=sap", sceneDiyProperty.getUrl(), sceneDiyProperty.getApiKey()),
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

    /**
     * 生成预留的查询条件(资产状态为已预留,关联预留需求的ids)
     *
     * @param reservedIds
     * @return
     */
    public List<CmdbModelParams.QueryCondition> generateReservedConditions(Set<String> reservedIds) {
        CmdbModelParams.QueryCondition assetQueryCondition1 = new CmdbModelParams.QueryCondition();
        assetQueryCondition1.setField("asset_status");
        assetQueryCondition1.setValue("reserved");
        CmdbModelParams.QueryCondition assetQueryCondition2 = new CmdbModelParams.QueryCondition();
        assetQueryCondition2.setField("reserve_ref");
        assetQueryCondition2.setOperator("IN");
        assetQueryCondition2.setValue(reservedIds);
        List<CmdbModelParams.QueryCondition> assetConditions = new ArrayList<>();
        assetConditions.add(assetQueryCondition1);
        assetConditions.add(assetQueryCondition2);
        return assetConditions;
    }

    /**
     * 生成预留的查询条件(资产状态为已预留,关联预留需求的ids,到期日期)
     *
     * @param reservedIds
     * @param beforeDate
     * @param afterDate
     * @return
     */
    public List<CmdbModelParams.QueryCondition> generateReservedConditions(Set<String> reservedIds, String beforeDate, String afterDate) {
        CmdbModelParams.QueryCondition assetQueryCondition1 = new CmdbModelParams.QueryCondition();
        assetQueryCondition1.setField("asset_status");
        assetQueryCondition1.setValue("reserved");
        CmdbModelParams.QueryCondition assetQueryCondition2 = new CmdbModelParams.QueryCondition();
        assetQueryCondition2.setField("reserve_ref");
        assetQueryCondition2.setOperator("IN");
        assetQueryCondition2.setValue(reservedIds);
        // 查询条件:日期
        List<String> expiryDateList = new ArrayList<>();
        expiryDateList.add(beforeDate + " 00:00:00");
        expiryDateList.add(afterDate + " 23:59:59");
        CmdbModelParams.QueryCondition assetQueryCondition3 = new CmdbModelParams.QueryCondition();
        assetQueryCondition3.setField("reserv_borrow_expiry_date");
        assetQueryCondition3.setOperator("RANGE");
        assetQueryCondition3.setValue(expiryDateList);
        CmdbModelParams.QueryCondition assetQueryCondition4 = new CmdbModelParams.QueryCondition();
        assetQueryCondition4.setField("reserved_chg");
        assetQueryCondition4.setOperator("NOT_EQ");
        assetQueryCondition4.setValue("false");
        List<CmdbModelParams.QueryCondition> assetConditions = new ArrayList<>();
        assetConditions.add(assetQueryCondition1);
        assetConditions.add(assetQueryCondition2);
        assetConditions.add(assetQueryCondition3);
        assetConditions.add(assetQueryCondition4);
        return assetConditions;
    }

    /**
     * 生成释放预留的查询条件(资产状态为已预留,关联预留需求的ids,到期日期)
     *
     * @param reservedIds
     * @param expiryDate
     * @return
     */
    public List<CmdbModelParams.QueryCondition> generateReleaseReservedConditions(Set<String> reservedIds, String expiryDate) {
        CmdbModelParams.QueryCondition assetQueryCondition1 = new CmdbModelParams.QueryCondition();
        assetQueryCondition1.setField("asset_status");
        assetQueryCondition1.setValue("reserved");
        CmdbModelParams.QueryCondition assetQueryCondition2 = new CmdbModelParams.QueryCondition();
        assetQueryCondition2.setField("reserve_ref");
        assetQueryCondition2.setOperator("IN");
        assetQueryCondition2.setValue(reservedIds);
        // 查询条件:日期
        List<String> expiryDateList = new ArrayList<>();
        expiryDateList.add(expiryDate + " 00:00:00");
        expiryDateList.add(expiryDate + " 23:59:59");
        CmdbModelParams.QueryCondition assetQueryCondition3 = new CmdbModelParams.QueryCondition();
        assetQueryCondition3.setField("reserv_borrow_expiry_date");
        assetQueryCondition3.setOperator("RANGE");
        assetQueryCondition3.setValue(expiryDateList);
//        CmdbModelParams.QueryCondition assetQueryCondition4 = new CmdbModelParams.QueryCondition();
//        assetQueryCondition4.setField("reserved_chg");
//        assetQueryCondition4.setValue("false");
        List<CmdbModelParams.QueryCondition> assetConditions = new ArrayList<>();
        assetConditions.add(assetQueryCondition1);
        assetConditions.add(assetQueryCondition2);
        assetConditions.add(assetQueryCondition3);
//        assetConditions.add(assetQueryCondition4);
        return assetConditions;
    }


    public Map<String, String> getDictMap(String dictCode) {
        Map<String, String> dictMap = new HashMap<>();
        String requestGet = HttpUtil.doRequestGet(sceneDiyProperty.getUrl() + "/cmdb/api/v3/model/dict/query", String.format("value=%s&apikey=%s", dictCode, sceneDiyProperty.getApiKey()));
        if (StringUtil.isNotEmpty(requestGet)) {
            JSONObject jsonObject = JSON.parseObject(requestGet);
            if (jsonObject != null) {
                JSONArray dataList = jsonObject.getJSONArray("dataList");
                if (CollectionUtils.isNotEmpty(dataList)) {
                    JSONObject object = (JSONObject) dataList.get(0);
                    if (object != null) {
                        JSONArray items = object.getJSONArray("items");
                        if (CollectionUtils.isNotEmpty(items)) {
                            for (Object item : items) {
                                JSONObject itemObject = (JSONObject) item;
                                if (itemObject != null) {
                                    dictMap.put(itemObject.getString("code"), itemObject.getString("name"));
                                }
                            }
                        }
                    }
                }
            }
        }
        return dictMap;
    }

    /**
     * 生成组合Key（main_device_type|manufacturer.name|manufacturer_model.name|host_type）
     * 通用适配 PhysicalAsset 和 AssetsReserveDetail
     */
    private static String generateCombinedKey(String mainDeviceType, BaseObject mf, BaseObject model, String hostType) {
        // 空值兜底：避免null导致Key拼接异常
        String mdt = Optional.ofNullable(mainDeviceType).orElse("");
        String mfName = Optional.ofNullable(mf).map(BaseObject::getName).orElse("");
        String modelName = Optional.ofNullable(model).map(BaseObject::getName).orElse("");
        String ht = Optional.ofNullable(hostType).orElse("");
        return String.join("|", mdt, mfName, modelName, ht);
    }

    /**
     * 生成组合Key（accessory_type|manufacturer.name|manufacturer_model.name）
     * 通用适配 AccessoryAsset 和 AssetsReserveDetail
     */
    private static String generateCombinedKey(String accessoryType, BaseObject mf, BaseObject model) {
        // 空值兜底：避免null导致Key拼接异常
        String at = Optional.ofNullable(accessoryType).orElse("");
        String mfName = Optional.ofNullable(mf).map(BaseObject::getName).orElse("");
        String modelName = Optional.ofNullable(model).map(BaseObject::getName).orElse("");
        return String.join("|", at, mfName, modelName);
    }

    /**
     * 统计 PhysicalAsset 按组合Key分组的数量
     */
    private static Map<String, Long> countPhysicalAssetByKey(List<PhysicalAsset> physicalAssets) {
        return Optional.ofNullable(physicalAssets).orElse(new ArrayList<>()).stream()
                .map(pa -> generateCombinedKey(
                        pa.getMain_device_type(),
                        pa.getManufacturer(),
                        pa.getManufactModel(),
                        pa.getHost_type()
                ))
                .collect(Collectors.groupingBy(
                        key -> key,
                        // 统计数量
                        Collectors.counting()
                ));
    }

    /**
     * 统计 AccessoryAsset 按组合Key分组的数量
     */
    private static Map<String, Long> countAccessoryAssetByKey(List<AccessoryAsset> accessoryAssets) {
        return Optional.ofNullable(accessoryAssets).orElse(new ArrayList<>()).stream()
                .map(aa -> generateCombinedKey(
                        aa.getAccessory_type(),
                        aa.getManufacturer(),
                        aa.getManufactModel()
                ))
                .collect(Collectors.groupingBy(
                        key -> key,
                        // 统计数量
                        Collectors.counting()
                ));
    }

    /**
     * 提取 AssetsReserveDetail 按组合Key分组的demando_count（确保每组唯一一条数据）
     */
    private static Map<String, AssetsReserveDetail> getReserveDemandCountByKey(List<AssetsReserveDetail> reserveDetails, Boolean isPhysical) {
        Map<String, AssetsReserveDetail> demandCountMap = new HashMap<>();

        // 先按组合Key分组(区分设备和配件)
        Map<String, List<AssetsReserveDetail>> reserveGroupMap;
        if (isPhysical) {
            reserveGroupMap = Optional.ofNullable(reserveDetails).orElse(new ArrayList<>()).stream()
                    // 过滤demando_count为null的无效数据
                    .filter(detail -> detail.getDemando_count() != null)
                    .collect(Collectors.groupingBy(
                            detail -> generateCombinedKey(
                                    detail.getMain_device_type(),
                                    detail.getManufacturer(),
                                    detail.getManufacturer_model(),
                                    detail.getHost_type()
                            )));
        } else {
            reserveGroupMap = Optional.ofNullable(reserveDetails).orElse(new ArrayList<>()).stream()
                    // 过滤demando_count为null的无效数据
                    .filter(detail -> detail.getDemando_count() != null)
                    .collect(Collectors.groupingBy(
                            detail -> generateCombinedKey(
                                    detail.getAccessory_type(),
                                    detail.getManufacturer(),
                                    detail.getManufacturer_model()
                            )));
        }
        // 遍历分组，校验每组唯一一条数据，并提取demando_count
        for (Map.Entry<String, List<AssetsReserveDetail>> entry : reserveGroupMap.entrySet()) {
            String key = entry.getKey();
            List<AssetsReserveDetail> detailList = entry.getValue();

            // 校验：每组必须唯一一条数据（按业务要求）
            if (detailList.size() != 1) {
                log.info("警告：组合Key[" + key + "]对应" + detailList.size() + "条AssetsReserveDetail数据，仅取第一条");
            }

            // 提取第一条数据
            demandCountMap.put(key, detailList.get(0));
        }

        return demandCountMap;
    }

    /**
     * 核心对比方法：PhysicalAsset数量 < AssetsReserveDetail.demando_count
     */
    public List<Map<String, Object>> comparePhysicalVsReserveDemand(List<PhysicalAsset> physicalAssets,
                                                                    List<AssetsReserveDetail> reserveDetails,
                                                                    Set<String> reserved_user) {
        List<Map<String, Object>> physicalMapList = new ArrayList<>();

        // 1. 统计PhysicalAsset分组数量
        Map<String, Long> physicalCountMap = countPhysicalAssetByKey(physicalAssets);

        // 2. 提取AssetsReserveDetail分组的demando_count
        Map<String, AssetsReserveDetail> reserveDemandMap = getReserveDemandCountByKey(reserveDetails, true);

        // 3. 遍历所有组合Key（包含两个集合的所有Key）
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(physicalCountMap.keySet());
        allKeys.addAll(reserveDemandMap.keySet());

        // 设备类型字典
        Map<String, String> deviceTypeCode = getDictMap("AM_device_type_code");

        // 4. 逐组对比
        for (String key : allKeys) {
            // PhysicalAsset数量（默认0）
            long physicalCount = physicalCountMap.getOrDefault(key, 0L);
            // AssetsReserveDetail的demando_count（默认0）
            AssetsReserveDetail assetsReserveDetail = reserveDemandMap.get(key);
            int reserveDemand = assetsReserveDetail != null ? assetsReserveDetail.getDemando_count() : 0;

            // 核心逻辑：实际预留资产数量 < 预留需求数量
            if (physicalCount < reserveDemand) {
                // 添加预留人
                Set<String> accountSet = Optional.ofNullable(assetsReserveDetail.getReserved_user())
                        .orElse(new ArrayList<>())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(ReservedUser::getAccount)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                reserved_user.addAll(accountSet);

                Map<String, Object> physicalMap = new HashMap<>();

                List<String> device_type = new ArrayList<>();
                device_type.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}",
                        deviceTypeCode.get(assetsReserveDetail.getMain_device_type()), assetsReserveDetail.getMain_device_type()));
                physicalMap.put("device_type", device_type);
                List<String> manufacturer = new ArrayList<>();
                BaseObject manufacturer1 = assetsReserveDetail.getManufacturer() != null ? assetsReserveDetail.getManufacturer() : new BaseObject();
                manufacturer.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}", manufacturer1.getName(), manufacturer1.getId()));
                physicalMap.put("manufacturer", manufacturer);
                List<String> manufacturer_model = new ArrayList<>();
                BaseObject vendorModel = assetsReserveDetail.getManufacturer_model() != null ? assetsReserveDetail.getManufacturer_model() : new BaseObject();
                manufacturer_model.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}", vendorModel.getName(), vendorModel.getId()));
                physicalMap.put("manufacturer_model", manufacturer_model);
                List<String> host_type = new ArrayList<>();
                host_type.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}", assetsReserveDetail.getHost_type(), assetsReserveDetail.getHost_type()));
                physicalMap.put("host_type", host_type);
                physicalMap.put("number", reserveDemand);
                physicalMap.put("reserved_num1", physicalCount);
                physicalMap.put("applicant", String.join(";", accountSet));
                physicalMapList.add(physicalMap);
            }
        }
        return physicalMapList;
    }

    /**
     * 核心对比方法：AccessoryAsset数量 < AssetsReserveDetail.demando_count
     */
    public static List<Map<String, Object>> compareAccessoryVsReserveDemand(List<AccessoryAsset> accessoryAssets,
                                                                            List<AssetsReserveDetail> reserveDetails,
                                                                            Set<String> reserved_user) {
        List<Map<String, Object>> accessoryMapList = new ArrayList<>();

        // 1. 统计AccessoryAsset分组数量
        Map<String, Long> accessoryCountMap = countAccessoryAssetByKey(accessoryAssets);

        // 2. 提取AssetsReserveDetail分组的demando_count
        Map<String, AssetsReserveDetail> reserveDemandMap = getReserveDemandCountByKey(reserveDetails, false);

        // 3. 遍历所有组合Key（包含两个集合的所有Key）
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(accessoryCountMap.keySet());
        allKeys.addAll(reserveDemandMap.keySet());

        // 4. 逐组对比
        for (String key : allKeys) {
            // AccessoryAsset数量（默认0）
            long accessoryCount = accessoryCountMap.getOrDefault(key, 0L);
            // AssetsReserveDetail的demando_count（默认0）
            AssetsReserveDetail assetsReserveDetail = reserveDemandMap.get(key);
            int reserveDemand = assetsReserveDetail != null ? assetsReserveDetail.getDemando_count() : 0;

            // 核心逻辑：配件资产数量 < 预留需求数量
            if (accessoryCount < reserveDemand) {
                // 添加预留人
                Set<String> accountSet = Optional.ofNullable(assetsReserveDetail.getReserved_user())
                        .orElse(new ArrayList<>())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(ReservedUser::getAccount)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                reserved_user.addAll(accountSet);

                // 构建配件明细
                Map<String, Object> accessoryMap = new HashMap<>();
                List<String> accessory_type = new ArrayList<>();
                accessory_type.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}", assetsReserveDetail.getAccessory_type(), assetsReserveDetail.getAccessory_type()));
                accessoryMap.put("accessory_type", accessory_type);
                List<String> manufacturer1 = new ArrayList<>();
                BaseObject manufacturer = assetsReserveDetail.getManufacturer() != null ? assetsReserveDetail.getManufacturer() : new BaseObject();
                manufacturer1.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}", manufacturer.getName(), manufacturer.getId()));
                accessoryMap.put("manufacturer1", manufacturer1);
                List<String> vendor_model = new ArrayList<>();
                BaseObject vendorModel = assetsReserveDetail.getManufacturer_model() != null ? assetsReserveDetail.getManufacturer_model() : new BaseObject();
                vendor_model.add(String.format("{\"__name\":\"%s\",\"__id\":\"%s\"}", vendorModel.getName(), vendorModel.getId()));
                accessoryMap.put("vendor_model", vendor_model);
                accessoryMap.put("number", reserveDemand);
                accessoryMap.put("reserved_num2", accessoryCount);
                accessoryMap.put("applicant", String.join(";", accountSet));
                accessoryMapList.add(accessoryMap);
            }
        }
        return accessoryMapList;
    }

    /**
     * 核心方法：按consumable_type分类，累加demando_count和reserved_num，返回新的List<ConsumablesReserve>
     * 兼容逻辑：若consumable_type为空，自动从name字段解析（提取倒数第二个/指定层级的类型）
     *
     * @param originalList 原始耗材预留列表
     * @return 分类累加后的新List<ConsumablesReserve>（每个元素对应唯一consumable_type，数量为累加值）
     */
    public static Map<String, ConsumablesReserve> groupAndSumByConsumableType(List<ConsumablesReserve> originalList, Set<String> reserved_user) {
        // 按consumable_type分组，累加demando_count和reserved_num
        Map<String, ConsumablesReserve> groupMap = new HashMap<>();
        Map<String, Set<String>> accountMap = new HashMap<>();
        for (ConsumablesReserve reserve : originalList) {
            // 添加预留人
            Set<String> accountSet = Optional.ofNullable(reserve.getReserved_user())
                    .orElse(new ArrayList<>())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(ReservedUser::getAccount)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            reserved_user.addAll(accountSet);
            String type = parseConsumableTypeFromName(reserve.getName());
            // 累加逻辑：存在则累加，不存在则初始化新对象
            if (groupMap.containsKey(type)) {
                ConsumablesReserve existReserve = groupMap.get(type);
                // 累加需求数量
                existReserve.setDemando_count(existReserve.getDemando_count() + reserve.getDemando_count());
                // 累加预留数量
                existReserve.setReserved_num(existReserve.getReserved_num() + reserve.getReserved_num());
                // 记录预留人
                Set<String> existAccountSet = accountMap.get(type);
                existAccountSet.addAll(accountSet);
                accountMap.put(type, existAccountSet);

            } else {
                // 新建对象存储累加结果
                ConsumablesReserve sumReserve = new ConsumablesReserve();
                sumReserve.setDemando_count(reserve.getDemando_count());
                sumReserve.setReserved_num(reserve.getReserved_num());
                groupMap.put(type, sumReserve);
                // 记录预留人
                accountMap.put(type, accountSet);
            }
        }

        // 补充applicant申请人字段
        for (Map.Entry<String, ConsumablesReserve> entry : groupMap.entrySet()) {
            entry.getValue().setApplicant(String.join(";", accountMap.get(entry.getKey())));
        }

        return groupMap;
    }

    /**
     * 辅助方法：从name字段解析consumable_type
     * 示例name：公有云备品备件预留/网线/超五类网线-5M/ 00.A3-WH501.SHLGS1 → 解析出"超五类网线-5M"
     */
    private static String parseConsumableTypeFromName(String name) {

        if (StringUtil.isEmpty(name)) return "";

        // 按"/"分割，过滤空字符串，去除首尾空格
        List<String> nameParts = Arrays.stream(name.split("/"))
                .map(String::trim)
                .filter(part -> StringUtil.isNotEmpty(part))
                .collect(Collectors.toList());

        // 解析规则：至少2个有效部分时，取倒数第二个（可根据实际业务调整）
        if (nameParts.size() == 4) {
            return nameParts.get(1) + ";" + nameParts.get(2);
        }
        // 不足则数据有问题，返回空字符串
        return "";
    }

    public static Map<String, String> calculateBeforeAndAfterDate(Integer day) {
        DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, String> resultMap = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate beforeDate = null;
        LocalDate afterDate = null;

        // 如果day不为空，则加上day天
        if (day != null) {
            today = today.plusDays(day);
        }

        switch (today.getDayOfWeek()) {
            case MONDAY:
            case TUESDAY:
                // 周一、周二：+3天= 周四、周五
                beforeDate = today.plusDays(3);
                afterDate = today.plusDays(3);
                break;
            case WEDNESDAY:
                // 周三：before=+3天(周六)，after=+5天(下周一)
                beforeDate = today.plusDays(3);
                afterDate = today.plusDays(5);
                break;
            case THURSDAY:
                // 周四：+3天（跳过周末）=下周二（实际+5天）
                beforeDate = today.plusDays(5);
                afterDate = today.plusDays(5);
                break;
            case FRIDAY:
                // 周五：+3天（跳过周末）=下周三（实际+4天）
                beforeDate = today.plusDays(4);
                afterDate = today.plusDays(4);
                break;
            case SATURDAY:
            case SUNDAY:
                break;
            default:
                break;
        }
        // 周六周日跳过
        if (beforeDate == null || afterDate == null) return resultMap;

        // 格式化日期为指定字符串格式
        resultMap.put("beforeDate", beforeDate.format(DATE_FORMATTER) );
        resultMap.put("afterDate", afterDate.format(DATE_FORMATTER));
        return resultMap;
    }


    public static void main(String[] args) {
        JSONObject jsonObject = JSON.parseObject("{\"totalRecords\":2,\"dataList\":[{\"id\":\"693d321cc74f7c0c3f420b6c\",\"classCode\":\"assets_reserve_detail\",\"name\":\"公有云备品备件预留/配件/光模块/新华三（H3C）/QSFP-100G-SR4-MM850/#\",\"asset_purpose\":\"公有云备品备件预留\",\"asset_type\":\"配件\",\"reserved_user\":[{\"uid\":\"e10adc3949ba59abbe56e057f20f88dd\",\"name\":\"admin\",\"account\":\"admin\"}],\"accessory_type\":\"光模块\",\"Manufacturer\":{\"name\":\"新华三（H3C）\",\"id\":\"68d3cd33f43b265182ea30c6\"},\"manufacturer_model\":{\"name\":\"QSFP-100G-SR4-MM850\",\"id\":\"68d9e8531f7a0c2026a475fe\"},\"link_assets_reserve\":{\"name\":\"公有云备品备件预留/配件\",\"id\":\"6927fc878a95f837bf14e7ec\"},\"demando_count\":2},{\"id\":\"693d321cc74f7c0c3f420b6b\",\"classCode\":\"assets_reserve_detail\",\"name\":\"公有云备品备件预留/设备/安全设备/昆仑（KunLun）/G8600/910B GPU Server(8462Y+)\",\"asset_purpose\":\"公有云备品备件预留\",\"asset_type\":\"设备\",\"reserved_user\":[{\"uid\":\"e10adc3949ba59abbe56e057f20f88dd\",\"name\":\"admin\",\"account\":\"admin\"}],\"main_device_type\":\"securityDev\",\"Manufacturer\":{\"name\":\"昆仑（KunLun）\",\"id\":\"68f6ee1d5de59e40f5b91bca\"},\"manufacturer_model\":{\"name\":\"G8600\",\"id\":\"68f6ee655de59e40f5b91bcd\"},\"host_type\":\"910B GPU Server(8462Y+)\",\"link_assets_reserve\":{\"name\":\"公有云备品备件预留/设备\",\"id\":\"6927fc878a95f837bf14e7eb\"},\"demando_count\":2}]}" );


        JSONArray dataArray = jsonObject.getJSONArray("dataList");

        List<AssetsReserveDetail> assetsReserveDetailList = JSON.parseArray(dataArray.toJSONString(), AssetsReserveDetail.class);
        // 设备的公有云预留需求明细
        List<AssetsReserveDetail> physicalReserveDetails = assetsReserveDetailList.stream()
                .filter(assetReserve -> "设备".equals(assetReserve.getAsset_type())).collect(Collectors.toList());
        // 未查询到数据，终止循环


        String s = parseConsumableTypeFromName("公有云备品备件预留/网线/超五类网线-5M/ 00.A3-WH501.SHLGS1");


        System.out.println(1);
    }

}
