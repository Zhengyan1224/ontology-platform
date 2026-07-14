package org.zhengyan.ontology.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/graphql-playground/").setViewName("forward:/graphql-playground/index.html");
        registry.addViewController("/mapping-assistant/").setViewName("forward:/mapping-assistant/index.html");
        registry.addViewController("/nlq/").setViewName("forward:/nlq/index.html");
        registry.addViewController("/nlq-examples/").setViewName("forward:/nlq-examples/index.html");
        registry.addViewController("/ontology-viz/").setViewName("forward:/ontology-viz/index.html");
        registry.addViewController("/query-history/").setViewName("forward:/query-history/index.html");
        registry.addViewController("/saved-queries/").setViewName("forward:/saved-queries/index.html");
        registry.addViewController("/tenant/").setViewName("forward:/tenant/index.html");
    }
}
