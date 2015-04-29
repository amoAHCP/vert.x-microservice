package org.jacpfx.common.spi;

/**
 * This is a SPI interface vor JSON converter implementations
 * Created by Andy Moncsek on 28.04.15.
 */
public interface JSONConverter {

    Object convertToObject(final String jsonString, Class<?> clazz);

    String convertToJSONString(final Object object);
}
