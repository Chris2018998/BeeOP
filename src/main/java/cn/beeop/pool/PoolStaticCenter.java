/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSourceConfigException;
import cn.beeop.pool.exception.ObjectException;
import cn.beeop.pool.exception.PoolClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    public static final Class[] EMPTY_CLASSES = new Class[0];
    public static final String[] EMPTY_CLASS_NAMES = new String[0];
    public static final int NCPUS = Runtime.getRuntime().availableProcessors();
    public static final Logger CommonLog = LoggerFactory.getLogger(PoolStaticCenter.class);

    //pool object state
    static final int OBJECT_IDLE = 0;
    static final int OBJECT_USING = 1;
    static final int OBJECT_CLOSED = 2;
    //pool state
    static final int POOL_NEW = 0;
    static final int POOL_READY = 1;
    static final int POOL_CLOSED = 2;
    static final int POOL_CLEARING = 3;
    //pool thread state
    static final int THREAD_WORKING = 0;
    static final int THREAD_WAITING = 1;
    static final int THREAD_EXIT = 2;
    //remove reason
    static final String DESC_RM_INIT = "init";
    static final String DESC_RM_BAD = "bad";
    static final String DESC_RM_IDLE = "idle";
    static final String DESC_RM_CLOSED = "closed";
    static final String DESC_RM_CLEAR = "clear";
    static final String DESC_RM_DESTROY = "destroy";

    static final Exception RequestTimeoutException = new ObjectException("Request timeout");
    static final Exception RequestInterruptException = new ObjectException("Request interrupted");
    static final Exception PoolCloseException = new PoolClosedException("Pool has shut down or in clearing");
    static final Exception ObjectClosedException = new ObjectException("No operations allowed after object handle closed");
    static final Exception ObjectMethodForbiddenException = new ObjectException("Method illegal access");
    static final ClassLoader PoolClassLoader = FastObjectPool.class.getClassLoader();
    private static final String Separator_MiddleLine = "-";
    private static final String Separator_UnderLine = "_";

    //***************************************************************************************************************//
    //                               1: Handle create/close methods(2)                                                  //
    //***************************************************************************************************************//
    static BeeObjectHandle createObjectHandle(PooledObject p, Borrower b) {
        b.lastUsed = p;
        return new ObjectHandle(p);
    }

    static void tryClosedProxyHandle(BeeObjectHandle handle) {
        try {
            handle.close();
        } catch (Throwable e) {
        }
    }

    //***************************************************************************************************************//
    //                               2: String operation methods(3)                                                  //
    //***************************************************************************************************************//
    public static String trimString(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public static boolean isBlank(String str) {
        if (str == null) return true;
        for (int i = 0, l = str.length(); i < l; ++i) {
            if (!Character.isWhitespace(str.charAt(i)))
                return false;
        }
        return true;
    }

    //***************************************************************************************************************//
    //                               3: configuration read methods(5)                                                //
    //***************************************************************************************************************//

    /**
     * find-out all set methods and put to map with method names,for example:
     * method:setMaxActive, map.put('MaxActive',method)
     *
     * @param beanClass set methods owner
     * @return methods map
     */
    public static Map<String, Method> getClassSetMethodMap(Class beanClass) {
        Method[] methods = beanClass.getMethods();
        HashMap<String, Method> methodMap = new LinkedHashMap<String, Method>(methods.length);
        for (Method method : methods) {
            String methodName = method.getName();
            if (method.getParameterTypes().length == 1 && methodName.startsWith("set") && methodName.length() > 3)
                methodMap.put(methodName.substring(3), method);
        }
        return methodMap;
    }

    /**
     * get config item value by property name,which support three format:
     * 1:hump,example:maxActive
     * 2:middle line,example: max-active
     * 3:middle line,example: max_active
     *
     * @param properties   configuration list
     * @param propertyName config item name
     * @return configuration item value
     */
    public static String getPropertyValue(Properties properties, String propertyName) {
        String value = PoolStaticCenter.readPropertyValue(properties, propertyName);
        if (value != null) return value;

        propertyName = propertyName.substring(0, 1).toLowerCase(Locale.US) + propertyName.substring(1);
        value = PoolStaticCenter.readPropertyValue(properties, propertyName);
        if (value != null) return value;

        value = PoolStaticCenter.readPropertyValue(properties, PoolStaticCenter.propertyNameToFieldId(propertyName, PoolStaticCenter.Separator_MiddleLine));
        if (value != null) return value;

        return PoolStaticCenter.readPropertyValue(properties, PoolStaticCenter.propertyNameToFieldId(propertyName, PoolStaticCenter.Separator_UnderLine));
    }

    /**
     * get config item value by property name,which support three format:
     * 1:hump,example:maxActive
     * 2:middle line,example: max-active
     * 3:middle line,example: max_active
     *
     * @param valueMap     configuration list
     * @param propertyName config item name
     * @return configuration item value
     */
    public static Object getFieldValue(Map<String, Object> valueMap, String propertyName) {
        Object value = valueMap.get(propertyName);
        if (value != null) return value;

        propertyName = propertyName.substring(0, 1).toLowerCase(Locale.US) + propertyName.substring(1);
        value = valueMap.get(propertyName);
        if (value != null) return value;

        value = valueMap.get(PoolStaticCenter.propertyNameToFieldId(propertyName, PoolStaticCenter.Separator_MiddleLine));
        if (value != null) return value;

        return valueMap.get(PoolStaticCenter.propertyNameToFieldId(propertyName, PoolStaticCenter.Separator_UnderLine));
    }

    private static String propertyNameToFieldId(String property, String separator) {
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

    private static String readPropertyValue(Properties configProperties, String propertyName) {
        String value = configProperties.getProperty(propertyName, null);
        if (value != null) {
            PoolStaticCenter.CommonLog.info("beeop.{}={}", propertyName, value);
            return value.trim();
        } else {
            return null;
        }
    }

    //***************************************************************************************************************//
    //                               4: bean property set methods(3)                                                 //
    //***************************************************************************************************************//
    public static void setPropertiesValue(Object bean, Map<String, Object> valueMap) throws BeeObjectSourceConfigException {
        if (bean == null) throw new BeeObjectSourceConfigException("Bean can't be null");
        PoolStaticCenter.setPropertiesValue(bean, PoolStaticCenter.getClassSetMethodMap(bean.getClass()), valueMap);
    }

    public static void setPropertiesValue(Object bean, Map<String, Method> setMethodMap, Map<String, Object> valueMap) throws BeeObjectSourceConfigException {
        if (bean == null) throw new BeeObjectSourceConfigException("Bean can't be null");
        if (setMethodMap == null || setMethodMap.isEmpty() || valueMap == null || valueMap.isEmpty()) return;
        for (Map.Entry<String, Method> entry : setMethodMap.entrySet()) {
            String propertyName = entry.getKey();
            Method setMethod = entry.getValue();

            Object setValue = PoolStaticCenter.getFieldValue(valueMap, propertyName);
            if (setValue != null) {
                Class type = setMethod.getParameterTypes()[0];
                try {
                    //1:convert config value to match type of set method
                    setValue = PoolStaticCenter.convert(propertyName, setValue, type);
                } catch (BeeObjectSourceConfigException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new BeeObjectSourceConfigException("Failed to convert config value to property(" + propertyName + ")type:" + type.getName(), e);
                }

                try {//2:inject value by set method
                    setMethod.invoke(bean, setValue);
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

    private static Object convert(String propName, Object setValue, Class type) {
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
        } else if (type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {//do nothing
            return null;
        } else {
            try {
                Object objInstance = Class.forName(text).newInstance();
                if (!type.isInstance(objInstance))
                    throw new BeeObjectSourceConfigException("Config value can't mach property(" + propName + ")type:" + type.getName());
                return objInstance;
            } catch (BeeObjectSourceConfigException e) {
                throw e;
            } catch (Throwable e) {
                throw new BeeObjectSourceConfigException("Failed to create property(" + propName + ")value by type:" + text, e);
            }
        }
    }

    //***************************************************************************************************************//
    //                               5:class check(3)                                                           //
    //***************************************************************************************************************//
    //check subclass,if failed,then return error message;
    public static String checkClass(Class objectClass, Class parentClass, String objectClassType) {
        return PoolStaticCenter.checkClass(objectClass, parentClass != null ? new Class[]{parentClass} : null, objectClassType);
    }

    //check subclass,if failed,then return error message;
    public static String checkClass(Class objectClass, Class[] parentClasses, String objectClassType) {
        //1:check extension
        boolean isSubClass = false;//pass when match one
        if (parentClasses != null && parentClasses.length > 0) {
            for (Class parentClass : parentClasses) {
                if (parentClass != null && parentClass.isAssignableFrom(objectClass)) {
                    isSubClass = true;
                    break;
                }
            }
            if (!isSubClass)
                return "Error " + objectClassType + " class[" + objectClass.getName() + "],which must extend from one of class[" + PoolStaticCenter.getClassName(parentClasses) + "]";
        }

        //2:check class abstract modifier
        if (Modifier.isAbstract(objectClass.getModifiers()))
            return "Error " + objectClassType + " class[" + objectClass.getName() + "],which can't be an abstract class";
        //3:check class public modifier
        if (!Modifier.isPublic(objectClass.getModifiers()))
            return "Error " + objectClassType + " class[" + objectClass.getName() + "],which must be a public class";
        //4:check class constructor
        try {
            objectClass.getConstructor(PoolStaticCenter.EMPTY_CLASSES);
        } catch (NoSuchMethodException e) {
            return "Error " + objectClassType + " class[" + objectClass.getName() + "],which must provide a constructor without parameter";
        }
        return null;
    }

    private static String getClassName(Class[] classes) {
        StringBuilder buf = new StringBuilder(classes.length * 10);
        for (Class clazz : classes) {
            if (buf.length() > 0) buf.append(",");
            buf.append(clazz.getName());
        }
        return buf.toString();
    }
}

