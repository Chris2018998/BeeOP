/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSourceConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Pool Static Center
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class PoolStaticCenter {
    //pool object state
    public static final int OBJECT_IDLE = 1;
    public static final int OBJECT_USING = 2;
    public static final int OBJECT_CLOSED = 3;
    //pool state
    public static final int POOL_UNINIT = 1;
    public static final int POOL_NORMAL = 2;
    public static final int POOL_CLOSED = 3;
    //pool thread state
    public static final int POOL_CLEARING = 4;
    public static final int THREAD_WORKING = 1;
    public static final int THREAD_WAITING = 2;
    public static final int THREAD_EXIT = 3;
    //remove reason
    public static final String DESC_RM_INIT = "init";
    public static final String DESC_RM_BAD = "bad";
    public static final String DESC_RM_IDLE = "idle";
    public static final String DESC_RM_CLOSED = "closed";
    public static final String DESC_RM_CLEAR = "clear";
    public static final String DESC_RM_DESTROY = "destroy";
    //borrower state
    public static final BorrowerState BOWER_NORMAL = new BorrowerState();
    public static final BorrowerState BOWER_WAITING = new BorrowerState();

    public static final Class[] EmptyParamTypes = new Class[0];
    public static final Object[] EmptyParamValues = new Object[0];
    public static final String OS_Config_Prop_Separator_MiddleLine = "-";
    public static final String OS_Config_Prop_Separator_UnderLine = "_";
    public static final Logger commonLog = LoggerFactory.getLogger(PoolStaticCenter.class);
    public static final BeeObjectException RequestTimeoutException = new BeeObjectException("Request timeout");
    public static final BeeObjectException RequestInterruptException = new BeeObjectException("Request interrupted");
    public static final BeeObjectException PoolCloseException = new BeeObjectException("Pool has shut down or in clearing");
    public static final BeeObjectException ObjectClosedException = new BeeObjectException("No operations allowed after object handle closed");
    public static final BeeObjectException ObjectMethodForbiddenException = new BeeObjectException("Method illegal access");
    private static final ClassLoader classLoader = FastObjectPool.class.getClassLoader();

    //*********************************************** 1 **************************************************************//
    public static final String trimString(String value) {
        return value == null ? null : value.trim();
    }

    public static final boolean isBlank(String str) {
        if (str == null) return true;
        int l = str.length();
        for (int i = 0; i < l; ++i) {
            if (!Character.isWhitespace((int) str.charAt(i)))
                return false;
        }
        return true;
    }

    static final void tryClosedProxyHandle(BeeObjectHandle handle) {
        try {
            handle.close();
        } catch (Throwable e) {
        }
    }

    //*********************************************** 2 **************************************************************//
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
                Object value;
                Class type = setMethod.getParameterTypes()[0];
                if (type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))//exclude container type properties
                    continue;

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
        HashMap<String, Method> methodMap = new LinkedHashMap<String, Method>(32);
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
                sb.append(separator).append(Character.toLowerCase(c));
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
        } else if (type == Class.class) {
            try {
                return Class.forName(text);
            } catch (ClassNotFoundException e) {
                throw new BeeObjectSourceConfigException("Not found class:" + text);
            }
        } else if (type.isArray()) {//do nothing
            return text;
        } else if (Collection.class.isAssignableFrom(type)) {//do nothing
            return text;
        } else if (Map.class.isAssignableFrom(type)) {//do nothing
            return text;
        } else {
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


    //*********************************************** 3 **************************************************************//
    static final class BorrowerState {//BORROWER STATE
    }

    static final class NullProxyObjectFactory implements ProxyObjectFactory {
        public final Object create(PooledEntry pEntry, BeeObjectHandle objectHandle, Set<String> excludeMethodNames) throws Exception {
            return null;
        }
    }

    static final class InvocationProxyObjectFactory implements ProxyObjectFactory {
        private Class[] objectInterfaces;

        public InvocationProxyObjectFactory(Class[] objectInterfaces) {
            this.objectInterfaces = objectInterfaces;
        }

        public final Object create(PooledEntry pEntry, BeeObjectHandle objectHandle, Set<String> excludeMethodNames) throws Exception {
            ProxyObjectHandler reflectHandler = new ProxyObjectHandler(pEntry, objectHandle, excludeMethodNames);
            return Proxy.newProxyInstance(
                    classLoader,
                    objectInterfaces,
                    reflectHandler);
        }
    }
}


