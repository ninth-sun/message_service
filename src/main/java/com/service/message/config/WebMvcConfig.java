package com.service.message.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <p>ProjectName: osp_cloud </p>
 * <p>Package: org.tsc.system.config.WebMvcConfig </p>
 * <p>ClassName: WebMvcConfig </p>
 * <p>Description: [MVC访问配置] </p>
 * <p>CreateDate: 2024/06/05 </p>
 *
 * @author <a href="mail to: " rel="nofollow">达卯科技</a>
 * @version v1.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * <p> @Method:addCorsMappings </p>
     * <p> @Description: [添加跨域配置] </p>
     * <p> @CreateDate: 2024/06/05 </p>
     *
     * @param registry cors资源
     * @author <a href="mail to: " rel="nofollow">达卯科技</a>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
