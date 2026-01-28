package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/28 16:57
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseObject {

    private String name;

    private String id;
}
