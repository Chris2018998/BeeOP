/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
 */
package cn.beeop;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.beeop.pool.StaticCenter.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Object pool configuration under object source
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeObjectSourceConfig implements BeeObjectSourceConfigJmxBean {
    //poolName index
    private static final AtomicInteger poolNameIndex = new AtomicInteger(1);
    private static final Class[] EMPTY_CLASSES = new Class[0];
    private static final String[] EMPTY_CLASS_NAMES = new String[0];
    //user name
    private String username;
    //password
    private String password;
    //server url
    private String serverUrl;
    //pool name
    private String poolName;
    //true:first arrive first take
    private boolean fairMode;
    //object created size at pool initialization
    private int initialSize;
    //object max size in pool
    private int maxActive = 10;
    //borrow Semaphore Size
    private int borrowSemaphoreSize = Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors());
    //milliseconds:borrower request timeout
    private long maxWait = SECONDS.toMillis(8);
    //milliseconds:idle timeout object will be removed from pool
    private long idleTimeout = MINUTES.toMillis(3);
    //milliseconds:long time not active object hold by borrower will closed by pool
    private long holdTimeout = MINUTES.toMillis(3);

    //seconds:max time to get check active result
    private int validTestTimeout = 3;
    //milliseconds:object test interval time from last active time
    private long validAssumeTime = 500L;
    //milliseconds:interval time to run check task in scheduledThreadPoolExecutor
    private long timerCheckInterval = MINUTES.toMillis(1);
    //using object close indicator,true,close directly;false,delay close util them becoming idle or hold timeout

    private boolean forceCloseUsingOnClear;
    //milliseconds:delay time for next clear pooled entry when exists using entry and 'forceCloseUsingOnClear' is false
    private long delayTimeForNextClear = 3000L;
    //object class
    private Class objectClass;
    //object class name
    private String objectClassName;
    //object implements interfaces
    private Class[] objectInterfaces;
    //object implements interface names
    private String[] objectInterfaceNames;
    //object factory
    private BeeObjectFactory objectFactory;
    //object factory class name
    private String objectFactoryClassName;
    //object create properties
    private Properties createProperties = new Properties();
    //exclude method names on raw object,which can't be called by user,for example;close,destroy,terminate
    private Set<String> excludeMethodNames = new HashSet<>(3);
    //pool implementation class name
    private String poolImplementClassName;
    //indicator,whether register pool to jmx
    private boolean enableJmx;
    //indicator,whether print pool config info
    private boolean enableConfigLog;
    //indicator,whether print pool runtime info
    private boolean enableRuntimeLog;

    public BeeObjectSourceConfig() {
        excludeMethodNames.add("close");
        excludeMethodNames.add("destroy");
        excludeMethodNames.add("terminate");
    }

    public BeeObjectSourceConfig(File propertiesFile) {
        this.loadFromPropertiesFile(propertiesFile);
    }

    public BeeObjectSourceConfig(String propertiesFileName) {
        this.loadFromPropertiesFile(propertiesFileName);
    }

    public BeeObjectSourceConfig(Properties configProperties) {
        this.loadFromProperties(configProperties);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = trimString(username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = trimString(password);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String jdbcUrl) {
        this.serverUrl = trimString(jdbcUrl);
    }

    @Override
    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = trimString(poolName);
    }

    @Override
    public boolean isFairMode() {
        return fairMode;
    }

    public void setFairMode(boolean fairMode) {
        this.fairMode = fairMode;
    }

    @Override
    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(int initialSize) {
        if (initialSize >= 0)
            this.initialSize = initialSize;
    }

    @Override
    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        if (maxActive > 0) {
            this.maxActive = maxActive;
            this.borrowSemaphoreSize = (maxActive > 1) ? Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors()) : 1;
        }
    }

    @Override
    public int getBorrowSemaphoreSize() {
        return borrowSemaphoreSize;
    }

    public void setBorrowSemaphoreSize(int borrowSemaphoreSize) {
        if (borrowSemaphoreSize > 0)
            this.borrowSemaphoreSize = borrowSemaphoreSize;
    }

    @Override
    public long getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(long maxWait) {
        if (maxWait > 0)
            this.maxWait = maxWait;
    }

    @Override
    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        if (idleTimeout > 0)
            this.idleTimeout = idleTimeout;
    }

    @Override
    public long getHoldTimeout() {
        return holdTimeout;
    }

    public void setHoldTimeout(long holdTimeout) {
        if (holdTimeout > 0)
            this.holdTimeout = holdTimeout;
    }

    @Override
    public int getValidTestTimeout() {
        return validTestTimeout;
    }

    public void setValidTestTimeout(int validTestTimeout) {
        if (validTestTimeout >= 0)
            this.validTestTimeout = validTestTimeout;
    }

    @Override
    public long getValidAssumeTime() {
        return validAssumeTime;
    }

    public void setValidAssumeTime(long validAssumeTime) {
        if (validAssumeTime >= 0)
            this.validAssumeTime = validAssumeTime;
    }

    @Override
    public long getTimerCheckInterval() {
        return timerCheckInterval;
    }

    public void setTimerCheckInterval(long timerCheckInterval) {
        if (timerCheckInterval > 0)
            this.timerCheckInterval = timerCheckInterval;
    }

    @Override
    public boolean isForceCloseUsingOnClear() {
        return forceCloseUsingOnClear;
    }

    public void setForceCloseUsingOnClear(boolean forceCloseUsingOnClear) {
        this.forceCloseUsingOnClear = forceCloseUsingOnClear;
    }

    @Override
    public long getDelayTimeForNextClear() {
        return delayTimeForNextClear;
    }

    public void setDelayTimeForNextClear(long delayTimeForNextClear) {
        if (delayTimeForNextClear > 0)
            this.delayTimeForNextClear = delayTimeForNextClear;
    }

    public Class getObjectClass() {
        return objectClass;
    }

    public void setObjectClass(Class objectClass) {
        this.objectClass = objectClass;
    }

    public String getObjectClassName() {
        return objectClassName;
    }

    public void setObjectClassName(String objectClassName) {
        this.objectClassName = trimString(objectClassName);
    }

    public Class[] getObjectInterfaces() {
        if (objectInterfaces == null)
            return EMPTY_CLASSES;

        Class[] tempInterfaces = new Class[objectInterfaces.length];
        System.arraycopy(objectInterfaces, 0, tempInterfaces, 0, objectInterfaces.length);
        return tempInterfaces;
    }

    public void setObjectInterfaces(Class[] interfaces) {
        if (interfaces == null) {
            this.objectInterfaces = null;
        } else {
            for (int i = 0, l = interfaces.length; i < l; i++) {
                if (interfaces[i] == null) throw new IllegalArgumentException("Parameter element[" + i + "]is null");
                if (!interfaces[i].isInterface())
                    throw new IllegalArgumentException("Parameter element[" + i + "]is not valid interface");
            }
            this.objectInterfaces = new Class[interfaces.length];
            System.arraycopy(interfaces, 0, objectInterfaces, 0, interfaces.length);
        }
    }

    public String[] getObjectInterfaceNames() {
        if (objectInterfaceNames == null) return EMPTY_CLASS_NAMES;
        String[] tempInterfaceNames = new String[objectInterfaceNames.length];
        System.arraycopy(objectInterfaceNames, 0, tempInterfaceNames, 0, objectInterfaceNames.length);
        return tempInterfaceNames;
    }

    public void setObjectInterfaceNames(String[] interfaceNames) {
        if (interfaceNames == null) {
            this.objectInterfaceNames = null;
        } else {
            for (int i = 0, l = interfaceNames.length; i < l; i++) {
                if (isBlank(interfaceNames[i]))
                    throw new IllegalArgumentException("Parameter element[" + i + "]is null or empty");
                try {
                    Class.forName(interfaceNames[i]);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Not found class:" + interfaceNames[i]);
                }
            }
            this.objectInterfaceNames = new String[interfaceNames.length];
            System.arraycopy(interfaceNames, 0, objectInterfaceNames, 0, interfaceNames.length);
        }
    }

    public BeeObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public void setObjectFactory(BeeObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public String getObjectFactoryClassName() {
        return objectFactoryClassName;
    }

    public void setObjectFactoryClassName(String objectFactoryClassName) {
        this.objectFactoryClassName = trimString(objectFactoryClassName);
    }

    public void removeCreateProperty(String key) {
        if (!isBlank(key))
            createProperties.remove(key);
    }

    public void addCreateProperty(String key, Object value) {
        if (!isBlank(key) && value != null)
            createProperties.put(key, value);
    }

    public Properties getCreateProperties() {
        return createProperties;
    }

    public Set<String> getExcludeMethodNames() {
        return excludeMethodNames;
    }

    public void addExcludeMethodName(String methodName) {
        if (!isBlank(methodName))
            excludeMethodNames.add(methodName);
    }

    public void removeExcludeMethodName(String methodName) {
        if (!isBlank(methodName))
            excludeMethodNames.remove(methodName);
    }

    @Override
    public String getPoolImplementClassName() {
        return poolImplementClassName;
    }

    public void setPoolImplementClassName(String poolImplementClassName) {
        this.poolImplementClassName = trimString(poolImplementClassName);
    }

    @Override
    public boolean isEnableJmx() {
        return enableJmx;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    private String trimString(String value) {
        return (value == null) ? null : value.trim();
    }

    public void setEnableConfigLog(boolean enableConfigLog) {
        this.enableConfigLog = enableConfigLog;
    }

    public boolean isEnableRuntimeLog() {
        return enableRuntimeLog;
    }

    public void setEnableRuntimeLog(boolean enableRuntimeLog) {
        this.enableRuntimeLog = enableRuntimeLog;
    }


    void copyTo(BeeObjectSourceConfig config) {
        //container type field
        List<String> excludeMethodNameList = new ArrayList(4);
        excludeMethodNameList.add("createProperties");
        excludeMethodNameList.add("excludeMethodNames");
        excludeMethodNameList.add("objectInterfaces");
        excludeMethodNameList.add("objectInterfaceNames");

        //1:primitive type copy
        Field[] fields = BeeObjectSourceConfig.class.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) continue;
            if (!excludeMethodNameList.contains(field.getName())) {
                try {
                    Object fieldValue = field.get(this);
                    if (enableConfigLog) commonLog.info("{}.{}={}", poolName, field.getName(), fieldValue);
                    field.set(config, fieldValue);
                } catch (Exception e) {
                    throw new BeeObjectSourceConfigException("Failed to copy field[" + field.getName() + "]", e);
                }
            }
        }

        //2:copy 'createProperties'
        Iterator<Map.Entry<Object, Object>> iterator = createProperties.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            if (enableConfigLog)
                commonLog.info("{}.createProperties.{}={}", poolName, entry.getKey(), entry.getValue());
            config.addCreateProperty((String) entry.getKey(), entry.getValue());
        }

        //3:copy 'excludeMethodNames'
        int index = 0;
        for (String methodName : excludeMethodNames) {
            config.addExcludeMethodName(methodName);
            if (enableConfigLog) commonLog.info("{}.excludeMethodNames[{}]={}", poolName, index++, methodName);
        }
        //4:copy 'objectInterfaces'
        Class[] interfaces = (objectInterfaces == null) ? null : new Class[objectInterfaces.length];
        if (interfaces != null) {
            System.arraycopy(objectInterfaces, 0, interfaces, 0, interfaces.length);
            for (int i = 0, l = interfaces.length; i < l; i++)
                if (enableConfigLog) commonLog.info("{}.objectInterfaces[{}]={}", poolName, i, interfaces[i]);
        }
        config.setObjectInterfaces(interfaces);

        //5:copy 'objectInterfaceNames'
        String[] interfaceNames = (objectInterfaceNames == null) ? null : new String[objectInterfaceNames.length];
        if (interfaceNames != null) {
            System.arraycopy(objectInterfaceNames, 0, interfaceNames, 0, interfaceNames.length);
            for (int i = 0, l = objectInterfaceNames.length; i < l; i++)
                if (enableConfigLog)
                    commonLog.info("{}.objectInterfaceNames[{}]={}", poolName, i, objectInterfaceNames[i]);
        }
        config.setObjectInterfaceNames(interfaceNames);
    }

    //check pool configuration
    public BeeObjectSourceConfig check() throws BeeObjectSourceConfigException {
        if (this.maxActive <= 0)
            throw new BeeObjectSourceConfigException("maxActive must be greater than zero");
        if (this.initialSize < 0)
            throw new BeeObjectSourceConfigException("initialSize must be greater than zero");
        if (this.initialSize > maxActive)
            throw new BeeObjectSourceConfigException("initialSize must not be greater than 'maxActive'");
        if (this.borrowSemaphoreSize <= 0)
            throw new BeeObjectSourceConfigException("borrowSemaphoreSize must be greater than zero");
        if (this.idleTimeout <= 0)
            throw new BeeObjectSourceConfigException("idleTimeout must be greater than zero");
        if (this.holdTimeout <= 0)
            throw new BeeObjectSourceConfigException("holdTimeout must be greater than zero");
        if (this.maxWait <= 0)
            throw new BeeObjectSourceConfigException("maxWait must be greater than zero");

        if (isBlank(this.poolName)) this.poolName = "FastPool-" + poolNameIndex.getAndIncrement();

        //1:load object implemented interfaces,if config
        Class[] objectInterfaces = loadObjectInterfaces();

        //2:try to create object factory
        BeeObjectFactory objectFactory = tryCreateObjectFactory();

        BeeObjectSourceConfig configCopy = new BeeObjectSourceConfig();
        this.copyTo(configCopy);
        configCopy.setObjectFactory(objectFactory);
        configCopy.setObjectInterfaces(objectInterfaces);

        if (!isBlank(password)) {
            this.addCreateProperty("user", username);
            configCopy.addCreateProperty("user", username);
        }
        if (!isBlank(password)) {
            this.addCreateProperty("password", password);
            configCopy.addCreateProperty("password", password);
        }
        if (!isBlank(serverUrl)) {
            this.addCreateProperty("serverUrl", serverUrl);
            configCopy.addCreateProperty("serverUrl", serverUrl);
        }
        return configCopy;
    }

    public void loadFromPropertiesFile(String filename) {
        if (isBlank(filename)) throw new IllegalArgumentException("Properties file can't be null");
        loadFromPropertiesFile(new File(filename));
    }

    public void loadFromPropertiesFile(File file) {
        if (file == null) throw new IllegalArgumentException("Properties file can't be null");
        if (!file.exists()) throw new IllegalArgumentException(file.getAbsolutePath());
        if (!file.isFile()) throw new IllegalArgumentException("Target object is not a valid file");
        if (!file.getAbsolutePath().toLowerCase(Locale.US).endsWith(".properties"))
            throw new IllegalArgumentException("Target file is not a properties file");

        InputStream stream = null;
        try {
            stream = Files.newInputStream(Paths.get(file.toURI()));
            Properties configProperties = new Properties();
            configProperties.load(stream);
            loadFromProperties(configProperties);
        } catch (BeeObjectSourceConfigException e) {
            throw (BeeObjectSourceConfigException) e;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Failed to load properties file:", e);
        } finally {
            if (stream != null) try {
                stream.close();
            } catch (Throwable e) {
            }
        }
    }

    public void loadFromProperties(Properties configProperties) {
        if (configProperties == null || configProperties.isEmpty())
            throw new IllegalArgumentException("Properties can't be null or empty");

        List<String> excludeMethodNameList = new ArrayList(4);
        excludeMethodNameList.add("excludeMethodNames");
        excludeMethodNameList.add("objectInterfaceNames");
        excludeMethodNameList.add("objectInterfaces");
        excludeMethodNameList.add("createProperties");

        //1:get all properties set methods
        Map<String, Method> setMethodMap = getSetMethodMap(BeeObjectSourceConfig.class);
        //2:create properties to collect config value
        Map<String, Object> setValueMap = new HashMap<String, Object>(setMethodMap.size());
        //3:loop to find out properties config value by set methods
        Iterator<String> iterator = setMethodMap.keySet().iterator();
        while (iterator.hasNext()) {
            String propertyName = iterator.next();
            if (!excludeMethodNameList.contains(propertyName)) {
                String configVal = getConfigValue(configProperties, propertyName);
                if (isBlank(configVal)) continue;
                setValueMap.put(propertyName, configVal);
            }
        }

        //4:inject found config value to ds config object
        setPropertiesValue(this, setMethodMap, setValueMap);

        //5:try to find 'excludeMethodNames' config value and put to ds config object
        String excludeMethodNames = getConfigValue(configProperties, "excludeMethodNames");
        if (!isBlank(excludeMethodNames)) {
            String[] excludeMethodNameArray = excludeMethodNames.split(",");
            for (String excludeMethodName : excludeMethodNameArray) {
                if (!isBlank(excludeMethodName)) {
                    excludeMethodName = excludeMethodName.trim();
                    this.addExcludeMethodName(excludeMethodName);
                    commonLog.debug("add excludeMethodName:{}", excludeMethodName);
                }
            }
        }

        //6:try to find 'excludeMethodNames' config value and put to ds config object
        String objectInterfaceNames = getConfigValue(configProperties, "objectInterfaceNames");
        if (!isBlank(objectInterfaceNames))
            this.setObjectInterfaceNames(objectInterfaceNames.split(","));

        //7:try to find 'excludeMethodNames' config value and put to ds config object
        String objectInterfaceNames2 = getConfigValue(configProperties, "objectInterfaces");
        if (!isBlank(objectInterfaceNames2)) {
            String[] objectInterfaceNameArray = objectInterfaceNames2.split(",");
            Class[] objectInterfaces = new Class[objectInterfaceNameArray.length];
            for (int i = 0, l = objectInterfaceNameArray.length; i < l; i++) {
                try {
                    objectInterfaces[i] = Class.forName(objectInterfaceNameArray[i]);
                } catch (ClassNotFoundException e) {
                    throw new BeeObjectSourceConfigException("Class not found:" + objectInterfaceNameArray[i]);
                }
            }
            this.setObjectInterfaces(objectInterfaces);
        }
        //8:try to find 'createProperties' config value and put to ds config object
        String connectPropVal = getConfigValue(configProperties, "createProperties");
        if (!isBlank(connectPropVal)) {
            String[] attributeArray = connectPropVal.split("&");
            for (String attribute : attributeArray) {
                String[] pairs = attribute.split("=");
                if (pairs.length == 2) {
                    this.addCreateProperty(pairs[0].trim(), pairs[1].trim());
                    commonLog.debug("beeop.createProperties.{}={}", pairs[0].trim(), pairs[1].trim());
                }
            }
        }
    }

    private final String getConfigValue(Properties configProperties, String propertyName) {
        String value = readConfig(configProperties, propertyName);
        if (isBlank(value))
            value = readConfig(configProperties, propertyNameToFieldId(propertyName, OS_Config_Prop_Separator_MiddleLine));
        if (isBlank(value))
            value = readConfig(configProperties, propertyNameToFieldId(propertyName, OS_Config_Prop_Separator_UnderLine));
        return value;
    }

    private final String readConfig(Properties configProperties, String propertyName) {
        String value = configProperties.getProperty(propertyName);
        if (!isBlank(value)) {
            commonLog.debug("beeop.{}={}", propertyName, value);
            return value.trim();
        } else {
            return null;
        }
    }

    private final Class[] loadObjectInterfaces() throws BeeObjectSourceConfigException {
        if (objectInterfaces != null) return this.objectInterfaces;
        if (objectInterfaceNames != null) {
            Class[] interfaces = new Class[objectInterfaceNames.length];
            ClassLoader loader = BeeObjectSourceConfig.class.getClassLoader();
            for (int i = 0, l = interfaces.length; i < l; i++) {
                try {
                    if (isBlank(objectInterfaceNames[i]))
                        throw new BeeObjectSourceConfigException("objectInterfaceNames[" + i + "]is empty");
                    interfaces[i] = Class.forName(objectInterfaceNames[i], true, loader);
                    if (!interfaces[i].isInterface())
                        throw new BeeObjectSourceConfigException("objectInterfaceNames[" + i + "]is not an interface");
                } catch (ClassNotFoundException e) {
                    throw new BeeObjectSourceConfigException("Not found object interface:" + objectInterfaceNames[i]);
                }
            }
            return interfaces;
        }
        return new Class[0];
    }

    private final BeeObjectFactory tryCreateObjectFactory() throws BeeObjectSourceConfigException {
        if (objectFactory != null) return objectFactory;
        if (!isBlank(objectFactoryClassName)) {
            try {
                Class<?> conFactClass = Class.forName(objectFactoryClassName, true, BeeObjectSourceConfig.class.getClassLoader());
                if (BeeObjectFactory.class.isAssignableFrom(conFactClass)) {
                    return (BeeObjectFactory) conFactClass.newInstance();
                } else {
                    throw new BeeObjectSourceConfigException("Error object factory class,must implement '" + BeeObjectFactory.class.getName() + "' interface");
                }
            } catch (ClassNotFoundException e) {
                throw new BeeObjectSourceConfigException("Not found object factory class:" + objectFactoryClassName);
            } catch (InstantiationException e) {
                throw new BeeObjectSourceConfigException("Failed to instantiate object factory class:" + objectFactoryClassName, e);
            } catch (IllegalAccessException e) {
                throw new BeeObjectSourceConfigException("Failed to instantiate object factory class:" + objectFactoryClassName, e);
            }
        } else {
            if (objectClass != null) return new BeeClassObjectFactory(objectClass);
            if (!isBlank(objectClassName)) {
                try {
                    objectClass = Class.forName(objectClassName, true, BeeObjectSourceConfig.class.getClassLoader());
                    if (Modifier.isAbstract(objectClass.getModifiers()))
                        throw new BeeObjectSourceConfigException("Object class is abstract,can't be instantiated");
                    try {
                        objectClass.getConstructor(new Class[0]);
                    } catch (NoSuchMethodException e) {
                        throw new BeeObjectSourceConfigException("Missed no parameter constructor in object class:" + objectClassName);
                    }
                    return new BeeClassObjectFactory(objectClass);
                } catch (ClassNotFoundException e) {
                    throw new BeeObjectSourceConfigException("Not found object class:" + objectClassName);
                }
            } else {
                throw new BeeObjectSourceConfigException("objectClassName can't be null");
            }
        }
    }
}

