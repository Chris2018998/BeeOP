/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectSourceConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pool Static Center
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class StaticCenter {
    //POOL STATE
    public static final int POOL_UNINIT = 1;
    public static final int POOL_NORMAL = 2;
    public static final int POOL_CLOSED = 3;
    public static final int POOL_RESTING = 4;
    //POOLED OBJECT STATE
    public static final int OBJECT_IDLE = 1;
    public static final int OBJECT_USING = 2;
    public static final int OBJECT_CLOSED = 3;
    //ADD OBJECT THREAD STATE
    public static final int THREAD_WORKING = 1;
    public static final int THREAD_WAITING = 2;
    public static final int THREAD_DEAD = 3;
    //BORROWER STATE
    public static final Object BORROWER_NORMAL = new Object();
    public static final Object BORROWER_WAITING = new Object();
    public static final BeeObjectException RequestTimeoutException = new BeeObjectException("Request timeout");
    public static final BeeObjectException RequestInterruptException = new BeeObjectException("Request interrupted");
    public static final BeeObjectException PoolCloseException = new BeeObjectException("Pool has shut down or in clearing");
    public static final BeeObjectException ObjectClosedException = new BeeObjectException("No operations allowed after object handle closed");
    public static final BeeObjectException ObjectMethodForbiddenException = new BeeObjectException("Method illegal access");
    public static final Class[] EmptyParamTypes = new Class[0];
    public static final Object[] EmptyParamValues = new Object[0];
    public static final Logger commonLog = LoggerFactory.getLogger(StaticCenter.class);
    public static final String OS_Config_Prop_Separator_MiddleLine = "-";
    public static final String OS_Config_Prop_Separator_UnderLine = "_";
    static final ConcurrentHashMap<Object, Method> ObjectMethodMap = new ConcurrentHashMap<Object, Method>();
    static final Object genMethodCacheKey(String name, Class[] types) {
        return new MethodKey(name, types);
    }

    public static final boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public static final boolean isBlank(String str) {
        if (str == null) return true;
        int strLen = str.length();
        for (int i = 0; i < strLen; ++i) {
            if (!Character.isWhitespace(str.charAt(i)))
                return false;
        }
        return true;
    }

    static final class MethodKey {
        private String name;
        private Class[] types;

        public MethodKey(String name, Class[] types) {
            this.name = name;
            this.types = types;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodKey that = (MethodKey) o;
            return name.equals(that.name) &&
                    Arrays.equals(types, that.types);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name);
            result = 31 * result + Arrays.hashCode(types);
            return result;
        }
    }


    public static final void setPropertiesValue(Object bean, Map<String, Object> setValueMap) throws Exception {
        if (bean == null) throw new BeeObjectSourceConfigException("Bean can't be null");
        setPropertiesValue(bean, getSetMethodMap(bean.getClass()), setValueMap);
    }

    public static final void setPropertiesValue(Object bean, Map<String, Method> setMethodMap, Map<String, Object> setValueMap) throws BeeObjectSourceConfigException {
        if (bean == null) throw new BeeObjectSourceConfigException("Bean can't be null");
        if (setMethodMap == null) throw new BeeObjectSourceConfigException("Set method map can't be null");
        if (setMethodMap.isEmpty()) throw new BeeObjectSourceConfigException("Set method map can't be empty");
        if (setValueMap == null) throw new BeeObjectSourceConfigException("Properties value map can't be null");
        if (setValueMap.isEmpty()) throw new BeeObjectSourceConfigException("Properties value map can't be empty");

        Iterator<Map.Entry<String, Object>> iterator = setValueMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String propertyName = entry.getKey();
            Object setValue = entry.getValue();
            Method setMethod = setMethodMap.get(propertyName);
            if (setMethod != null && setValue != null) {
                Object value = null;
                Class type = setMethod.getParameterTypes()[0];

                try {//1:convert config value to match type of set method
                    value = convert(propertyName, setValue, type);
                } catch (BeeObjectSourceConfigException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BeeObjectSourceConfigException("Failed to convert config value to property(" + propertyName + ")type:" + type.getName(), e);
                }

                try {//2:inject value by set method
                    setMethod.invoke(bean, new Object[]{value});
                } catch (IllegalAccessException e) {
                    throw new BeeObjectSourceConfigException("Failed to inject config value to property:" + propertyName, e);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getTargetException();
                    if (cause != null) {
                        throw new BeeObjectSourceConfigException("Failed to inject config value to property:" + propertyName, cause);
                    } else {
                        throw new BeeObjectSourceConfigException("Failed to inject config value to property:" + propertyName, e);
                    }
                }
            }
        }
    }

    public static final Map<String, Method> getSetMethodMap(Class beanClass) {
        HashMap<String, Method> methodMap = new LinkedHashMap<String, Method>();
        Method[] methods = beanClass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.length() > 3 && methodName.startsWith("set") && method.getParameterTypes().length == 1) {
                methodName = methodName.substring(3);
                methodName = methodName.substring(0, 1).toLowerCase(Locale.US) + methodName.substring(1);
                methodMap.put(methodName, method);
            }
        }
        return methodMap;
    }

    public static final String propertyNameToFieldId(String property, String separator) {
        char[] chars = property.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        for (char c : chars) {
            if (Character.isUpperCase(c)) {
                sb.append(separator + Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final Object convert(String propName, Object setValue, Class type) throws BeeObjectSourceConfigException {
        if (type.isInstance(setValue)) {
            return setValue;
        } else if (type == String.class) {
            return setValue.toString();
        }

        String text = setValue.toString();
        text = text.trim();
        if (text.length() == 0) return null;

        if (type == char.class || type == Character.class) {
            return text.toCharArray()[0];
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(text);
        } else if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(text);
        } else if (type == short.class || type == Short.class) {
            return Short.parseShort(text);
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(text);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(text);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(text);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(text);
        } else if (type == BigInteger.class) {
            return new BigInteger(text);
        } else if (type == BigDecimal.class) {
            return new BigDecimal(text);
        } else if(type.isArray()){
            String[]elements=text.split(",");
            /**
             * @todo
             */
        } else{
            try {
                Object objInstance = Class.forName(text).newInstance();
                if (!type.isInstance(objInstance))
                    throw new BeeObjectSourceConfigException("Config value can't mach property(" + propName + ")type:" + type.getName());

                return objInstance;
            } catch (ClassNotFoundException e) {
                throw new BeeObjectSourceConfigException("Not found class:" + text);
            } catch (InstantiationException e) {
                throw new BeeObjectSourceConfigException("Failed to instantiated class:" + text, e);
            } catch (IllegalAccessException e) {
                throw new BeeObjectSourceConfigException("Failed to instantiated class:" + text, e);
            }
        }
    }
}


