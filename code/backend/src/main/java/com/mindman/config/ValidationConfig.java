package com.mindman.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Bean Validation 配置
 *
 * <p>启用方法级校验（Controller 参数上的 @Valid / @Validated）</p>
 * <p>快速失败模式：遇到第一个校验不通过的字段立即返回，不继续检查后续字段</p>
 */
@Configuration
public class ValidationConfig {

    @Bean
    public Validator validator() {
        return Validation.byProvider(HibernateValidator.class)
                .configure()
                .failFast(true)          // 快速失败
                .buildValidatorFactory()
                .getValidator();
    }

    /**
     * 开启 @Validated 注解的方法参数校验
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator());
        return processor;
    }
}