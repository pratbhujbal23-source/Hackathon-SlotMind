package com.slotmind.config;
import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*;
@Configuration public class WebConfig implements WebMvcConfigurer { @Override public void addCorsMappings(CorsRegistry r) { r.addMapping("/**").allowedOrigins("http://localhost:5173").allowedMethods("*"); } }
