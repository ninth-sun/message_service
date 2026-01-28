package com.service.message.config;

import com.alibaba.fastjson2.JSONObject;
import com.service.message.property.SceneDiyProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/11 15:50
 * @Description: Token鉴权拦截器
 */

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private SceneDiyProperty sceneDiyProperty;

    private static final String AUTHORIZATION = "Authorization";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头获取Token
        String token = request.getHeader(AUTHORIZATION);

        // 2. Token校验：为空/不匹配则返回401未授权
        if (token == null || !token.equals(sceneDiyProperty.getToken())) {
            response.setContentType("application/json;charset=UTF-8");
            // 401状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            PrintWriter writer = response.getWriter();
            // 返回JSON格式的错误信息
            JSONObject result = new JSONObject();
            result.put("code", 401);
            result.put("msg", "鉴权失败：Token为空或不合法");
            writer.write(result.toJSONString());
            writer.flush();
            writer.close();
            // 拦截请求，不执行后续接口逻辑
            return false;
        }

        // 3. Token合法，放行请求
        return true;
    }
}
