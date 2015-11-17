package org.jacpfx.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by Andy Moncsek on 31.07.15. Defines an ServiceEndpoint and his metadata. E Class Annotated wit @ServiceEndpoint must extend from ServiceVerticle
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceEndpoint {
    /**
     *   // the endpoint path
     * @return  the endpoint path
     */
    String value();

    /**
     *
     * @return The Endpoint Poort
     */
    int port() default 8080;
}
