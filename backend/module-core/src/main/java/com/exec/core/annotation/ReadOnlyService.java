package com.exec.core.annotation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * 읽기 전용 서비스를 위한 복합 애노테이션
 *
 * @Service + @Transactional(readOnly = true) 를 결합
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
@Transactional(readOnly = true)
public @interface ReadOnlyService {

    /**
     * @Service 의 value 속성과 동일
     */
    String value() default "";
}