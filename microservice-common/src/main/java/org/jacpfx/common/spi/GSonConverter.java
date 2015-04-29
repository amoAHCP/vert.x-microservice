package org.jacpfx.common.spi;

import com.google.gson.Gson;

/**
 * Created by Andy Moncsek on 29.04.15.
 */
public class GSonConverter implements JSONConverter {
    Gson gson = new Gson();
    @Override
    public Object convertToObject(String jsonString, Class<?> clazz) {
        return gson.fromJson(jsonString,clazz);
    }

    @Override
    public String convertToJSONString(Object object) {
        return null;
    }
}
