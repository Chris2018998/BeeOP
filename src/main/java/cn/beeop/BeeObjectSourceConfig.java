/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop;

import cn.beeop.pool.FastObjectPool;
import cn.beeop.pool.SimpleObjectFactory;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.beeop.pool.PoolStaticCenter.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Object pool configuration
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeObjectSourceConfig implements BeeObjectSourceConfigJmxBean {
    //poolName index
    private static final AtomicInteger PoolNameIndex = new AtomicInteger(1);

    //exclude method names on raw object,which can't be called by user,for example;close,destroy,terminate
    private final Set<String> excludeMethodNames = new HashSet<String>(3);
    //object factory properties
    private final Map<String, Object> factoryProperties = new HashMap<String, Object>(1);
    //pool name,if not set,auto generated by<code>BeeObjectSourceConfig.PoolNameIndex</code>
    private String poolName;
    //boolean indicator,true:pool will use fair semaphore and fair transfer policy;default value:false
    private boolean fairMode;
    //size of object instance on pool starting
    private int initialSize;
    //max reachable size of object instance in pool
    private int maxActive = Math.min(Math.max(10, NCPUS), 50);
    //max permit size of pool semaphore
    private int borrowSemaphoreSize = Math.min(this.maxActive / 2, NCPUS);
    //milliseconds:max wait time to get one object from pool<code>ObjectPool.getObject()</code>
    private long maxWait = SECONDS.toMillis(8);
    //milliseconds:max idle time of objects in pool,when reach,then close them and remove from pool
    private long idleTimeout = MINUTES.toMillis(3);
    //milliseconds:max no-use time of borrowed object,when reach,then return them to pool by forced close
    private long holdTimeout = MINUTES.toMillis(3);
    //seconds:max time to get valid test result
    private int validTestTimeout = 3;
    //milliseconds:objects valid assume time after last activity,if borrowed,not need test during the duration
    private long validAssumeTime = 500L;
    //milliseconds:interval time to run timer check task
    private long timerCheckInterval = MINUTES.toMillis(3);
    //using object instance forced close indicator on pool clean
    private boolean forceCloseUsingOnClear;
    //milliseconds:delay time for next loop to clear,when<code>forceCloseUsingOnClear</code> is false and exists using object instance
    private long delayTimeForNextClear = 3000L;

    //indicator,whether register pool to jmx
    private boolean enableJmx;
    //indicator,whether print pool config info
    private boolean printConfigInfo;
    //indicator,whether print pool runtime info
    private boolean printRuntimeLog;

    //object class
    private Class objectClass;
    //object class name
    private String objectClassName;
    //object implements interfaces
    private Class[] objectInterfaces;
    //object implements interface names
    private String[] objectInterfaceNames;
    //object factory class
    private Class objectFactoryClass;
    //object factory class name
    private String objectFactoryClassName;
    //object factory
    private RawObjectFactory objectFactory;
    //pool implementation class name
    private String poolImplementClassName = FastObjectPool.class.getName();

    //***************************************************************************************************************//
    //                                     1: constructors(4)                                                        //
    //***************************************************************************************************************//
    public BeeObjectSourceConfig() {
        this.excludeMethodNames.add("close");
        this.excludeMethodNames.add("destroy");
        this.excludeMethodNames.add("terminate");
    }

    //read configuration from properties file
    public BeeObjectSourceConfig(File propertiesFile) {
        this();
        loadFromPropertiesFile(propertiesFile);
    }

    //read configuration from properties file
    public BeeObjectSourceConfig(String propertiesFileName) {
        this();
        loadFromPropertiesFile(propertiesFileName);
    }

    //read configuration from properties
    public BeeObjectSourceConfig(Properties configProperties) {
        this();
        loadFromProperties(configProperties);
    }

    //***************************************************************************************************************//
    //                                     2:configuration about pool inner control(33)                              //
    //***************************************************************************************************************//
    public String getPoolName() {
        return this.poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = trimString(poolName);
    }

    public boolean isFairMode() {
        return this.fairMode;
    }

    public void setFairMode(boolean fairMode) {
        this.fairMode = fairMode;
    }

    public int getInitialSize() {
        return this.initialSize;
    }

    public void setInitialSize(int initialSize) {
        if (initialSize >= 0) this.initialSize = initialSize;
    }

    public int getMaxActive() {
        return this.maxActive;
    }

    public void setMaxActive(int maxActive) {
        if (maxActive > 0) {
            this.maxActive = maxActive;
            borrowSemaphoreSize = (maxActive > 1) ? Math.min(maxActive / 2, NCPUS) : 1;
        }
    }

    public int getBorrowSemaphoreSize() {
        return this.borrowSemaphoreSize;
    }

    public void setBorrowSemaphoreSize(int borrowSemaphoreSize) {
        if (borrowSemaphoreSize > 0) this.borrowSemaphoreSize = borrowSemaphoreSize;
    }

    public long getMaxWait() {
        return this.maxWait;
    }

    public void setMaxWait(long maxWait) {
        if (maxWait > 0) this.maxWait = maxWait;
    }

    public long getIdleTimeout() {
        return this.idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        if (idleTimeout > 0) this.idleTimeout = idleTimeout;
    }

    public long getHoldTimeout() {
        return this.holdTimeout;
    }

    public void setHoldTimeout(long holdTimeout) {
        if (holdTimeout > 0) this.holdTimeout = holdTimeout;
    }

    public int getValidTestTimeout() {
        return this.validTestTimeout;
    }

    public void setValidTestTimeout(int validTestTimeout) {
        if (validTestTimeout >= 0) this.validTestTimeout = validTestTimeout;
    }

    public long getValidAssumeTime() {
        return this.validAssumeTime;
    }

    public void setValidAssumeTime(long validAssumeTime) {
        if (validAssumeTime >= 0) this.validAssumeTime = validAssumeTime;
    }

    public long getTimerCheckInterval() {
        return this.timerCheckInterval;
    }

    public void setTimerCheckInterval(long timerCheckInterval) {
        if (timerCheckInterval > 0) this.timerCheckInterval = timerCheckInterval;
    }

    public boolean isForceCloseUsingOnClear() {
        return this.forceCloseUsingOnClear;
    }

    public void setForceCloseUsingOnClear(boolean forceCloseUsingOnClear) {
        this.forceCloseUsingOnClear = forceCloseUsingOnClear;
    }

    public long getDelayTimeForNextClear() {
        return this.delayTimeForNextClear;
    }

    public void setDelayTimeForNextClear(long delayTimeForNextClear) {
        if (delayTimeForNextClear >= 0) this.delayTimeForNextClear = delayTimeForNextClear;
    }

    public String getPoolImplementClassName() {
        return this.poolImplementClassName;
    }

    public void setPoolImplementClassName(String poolImplementClassName) {
        if (!isBlank(poolImplementClassName)) this.poolImplementClassName = trimString(poolImplementClassName);
    }

    public boolean isEnableJmx() {
        return this.enableJmx;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public void setPrintConfigInfo(boolean printConfigInfo) {
        this.printConfigInfo = printConfigInfo;
    }

    public boolean isPrintRuntimeLog() {
        return this.printRuntimeLog;
    }

    public void setPrintRuntimeLog(boolean printRuntimeLog) {
        this.printRuntimeLog = printRuntimeLog;
    }


    //***************************************************************************************************************//
    //                                     3: configuration about object creation(19)                                //
    //***************************************************************************************************************//
    public Class getObjectClass() {
        return this.objectClass;
    }

    public void setObjectClass(Class objectClass) {
        this.objectClass = objectClass;
    }

    public String getObjectClassName() {
        return this.objectClassName;
    }

    public void setObjectClassName(String objectClassName) {
        this.objectClassName = trimString(objectClassName);
    }

    public Class[] getObjectInterfaces() {
        if (this.objectInterfaces == null) return EMPTY_CLASSES;

        Class[] tempInterfaces = new Class[this.objectInterfaces.length];
        System.arraycopy(this.objectInterfaces, 0, tempInterfaces, 0, this.objectInterfaces.length);
        return tempInterfaces;
    }

    public void setObjectInterfaces(Class[] interfaces) {
        if (interfaces == null || interfaces.length == 0) {
            objectInterfaces = null;
        } else {
            objectInterfaces = new Class[interfaces.length];
            System.arraycopy(interfaces, 0, this.objectInterfaces, 0, interfaces.length);
        }
    }

    public String[] getObjectInterfaceNames() {
        if (this.objectInterfaceNames == null) return EMPTY_CLASS_NAMES;

        String[] tempInterfaceNames = new String[this.objectInterfaceNames.length];
        System.arraycopy(this.objectInterfaceNames, 0, tempInterfaceNames, 0, this.objectInterfaceNames.length);
        return tempInterfaceNames;
    }

    public void setObjectInterfaceNames(String[] interfaceNames) {
        if (interfaceNames == null || interfaceNames.length == 0) {
            objectInterfaceNames = null;
        } else {
            objectInterfaceNames = new String[interfaceNames.length];
            System.arraycopy(interfaceNames, 0, this.objectInterfaceNames, 0, interfaceNames.length);
        }
    }

    public Class getObjectFactoryClass() {
        return this.objectFactoryClass;
    }

    public void setObjectFactoryClass(Class objectFactoryClass) {
        this.objectFactoryClass = objectFactoryClass;
    }

    public String getObjectFactoryClassName() {
        return this.objectFactoryClassName;
    }

    public void setObjectFactoryClassName(String objectFactoryClassName) {
        this.objectFactoryClassName = trimString(objectFactoryClassName);
    }

    public RawObjectFactory getObjectFactory() {
        return this.objectFactory;
    }

    public void addExcludeMethodName(String methodName) {
        if (!isBlank(methodName)) this.excludeMethodNames.add(methodName);
    }

    public void removeExcludeMethodName(String methodName) {
        if (!isBlank(methodName)) this.excludeMethodNames.remove(methodName);
    }

    public Set<String> getExcludeMethodNames() {
        return new HashSet<String>(this.excludeMethodNames);
    }

    public void removeFactoryProperty(String key) {
        if (!isBlank(key)) this.factoryProperties.remove(key);
    }

    public void addFactoryProperty(String key, Object value) {
        if (!isBlank(key) && value != null) this.factoryProperties.put(key, value);
    }

    public void addFactoryProperty(String propertyText) {
        if (!isBlank(propertyText)) {
            String[] attributeArray = propertyText.split("&");
            for (String attribute : attributeArray) {
                String[] pair = attribute.split("=");
                if (pair.length == 2) {
                    this.factoryProperties.put(pair[0].trim(), pair[1].trim());
                } else {
                    pair = attribute.split(":");
                    if (pair.length == 2) {
                        this.factoryProperties.put(pair[0].trim(), pair[1].trim());
                    }
                }
            }
        }
    }

    //***************************************************************************************************************//
    //                                     4: configuration load from properties load (3)                            //
    //***************************************************************************************************************//
    public void loadFromPropertiesFile(String filename) {
        if (isBlank(filename)) throw new IllegalArgumentException("Properties file can't be null");
        this.loadFromPropertiesFile(new File(filename));
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
            this.loadFromProperties(configProperties);
        } catch (BeeObjectSourceConfigException e) {
            throw e;
        } catch (Throwable e) {
            throw new BeeObjectSourceConfigException("Failed to load properties file:", e);
        } finally {
            if (stream != null) try {
                stream.close();
            } catch (Throwable e) {
                //do nothing
            }
        }
    }

    public void loadFromProperties(Properties configProperties) {
        if (configProperties == null || configProperties.isEmpty())
            throw new IllegalArgumentException("Properties can't be null or empty");

        //1:load configuration item values from outside properties
        Map<String, Object> setValueMap = new HashMap<String, Object>(configProperties.size());
        for (String propertyName : configProperties.stringPropertyNames()) {
            setValueMap.put(propertyName, configProperties.getProperty(propertyName));
        }

        //2:inject item value from map to this dataSource config object
        setPropertiesValue(this, setValueMap);

        //3:try to find 'factoryProperties' config value
        this.addFactoryProperty(getPropertyValue(configProperties, "factoryProperties"));
        String factoryPropertiesCount = getPropertyValue(configProperties, "factoryProperties.count");
        if (!isBlank(factoryPropertiesCount)) {
            int count = 0;
            try {
                count = Integer.parseInt(factoryPropertiesCount.trim());
            } catch (Throwable e) {
                //do nothing
            }
            for (int i = 1; i <= count; i++)
                this.addFactoryProperty(getPropertyValue(configProperties, "factoryProperties." + i));
        }

        //4:try to find 'excludeMethodNames' config value
        String excludeMethodNames = getPropertyValue(configProperties, "excludeMethodNames");
        if (!isBlank(excludeMethodNames)) {
            String[] excludeMethodNameArray = excludeMethodNames.split(",");
            for (String excludeMethodName : excludeMethodNameArray) {
                if (!isBlank(excludeMethodName)) {
                    excludeMethodName = excludeMethodName.trim();
                    addExcludeMethodName(excludeMethodName);

                    CommonLog.debug("add excludeMethodName:{}", excludeMethodName);
                }
            }
        }

        //5:try to find 'objectInterfaceNames' config value
        String objectInterfaceNames = getPropertyValue(configProperties, "objectInterfaceNames");
        if (!isBlank(objectInterfaceNames))
            setObjectInterfaceNames(objectInterfaceNames.split(","));

        //6:try to find 'objectInterfaces' config value
        String objectInterfaceNames2 = getPropertyValue(configProperties, "objectInterfaces");
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
            setObjectInterfaces(objectInterfaces);
        }
    }

    //***************************************************************************************************************//
    //                                     5: configuration check and object factory create methods(4)               //
    //***************************************************************************************************************//
    //check pool configuration
    public BeeObjectSourceConfig check() {
        if (maxActive <= 0)
            throw new BeeObjectSourceConfigException("maxActive must be greater than zero");
        if (initialSize < 0)
            throw new BeeObjectSourceConfigException("initialSize must be greater than zero");
        if (initialSize > this.maxActive)
            throw new BeeObjectSourceConfigException("initialSize must not be greater than 'maxActive'");
        if (borrowSemaphoreSize <= 0)
            throw new BeeObjectSourceConfigException("borrowSemaphoreSize must be greater than zero");
        if (idleTimeout <= 0)
            throw new BeeObjectSourceConfigException("idleTimeout must be greater than zero");
        if (holdTimeout <= 0)
            throw new BeeObjectSourceConfigException("holdTimeout must be greater than zero");
        if (maxWait <= 0)
            throw new BeeObjectSourceConfigException("maxWait must be greater than zero");

        //1:load object implemented interfaces,if config
        Class[] objectInterfaces = this.loadObjectInterfaces();
        //2:try to create object factory
        RawObjectFactory objectFactory = this.tryCreateObjectFactory(objectInterfaces);
        if (isBlank(poolName)) poolName = "FastPool-" + PoolNameIndex.getAndIncrement();

        //3:create config object
        BeeObjectSourceConfig configCopy = new BeeObjectSourceConfig();
        copyTo(configCopy);
        configCopy.objectFactory = objectFactory;
        configCopy.setObjectInterfaces(objectInterfaces);
        return configCopy;
    }

    void copyTo(BeeObjectSourceConfig config) {
        List<String> containerTypeFieldList = new ArrayList<String>(4);
        containerTypeFieldList.add("objectInterfaces");
        containerTypeFieldList.add("objectInterfaceNames");
        containerTypeFieldList.add("excludeMethodNames");
        containerTypeFieldList.add("factoryProperties");

        //1:primitive type copy
        String fieldName = "";
        try {
            for (Field field : BeeObjectSourceConfig.class.getDeclaredFields()) {
                if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers()) || containerTypeFieldList.contains(field.getName()))
                    continue;
                Object fieldValue = field.get(this);
                fieldName = field.getName();

                if (this.printConfigInfo) CommonLog.info("{}.{}={}", this.poolName, fieldName, fieldValue);
                field.set(config, fieldValue);
            }
        } catch (Throwable e) {
            throw new BeeObjectSourceConfigException("Failed to copy field[" + fieldName + "]", e);
        }

        //2:copy 'excludeMethodNames'
        int index = 0;
        for (String methodName : this.excludeMethodNames) {
            config.addExcludeMethodName(methodName);
            if (this.printConfigInfo)
                CommonLog.info("{}.excludeMethodNames[{}]={}", this.poolName, index++, methodName);
        }

        //3:copy 'objectInterfaces'
        Class[] interfaces = (this.objectInterfaces == null) ? null : new Class[this.objectInterfaces.length];
        if (interfaces != null) {
            System.arraycopy(this.objectInterfaces, 0, interfaces, 0, interfaces.length);
            for (int i = 0, l = interfaces.length; i < l; i++)
                if (this.printConfigInfo) CommonLog.info("{}.objectInterfaces[{}]={}", this.poolName, i, interfaces[i]);
        }
        config.setObjectInterfaces(interfaces);

        //4:copy 'objectInterfaceNames'
        String[] interfaceNames = (this.objectInterfaceNames == null) ? null : new String[this.objectInterfaceNames.length];
        if (interfaceNames != null) {
            System.arraycopy(this.objectInterfaceNames, 0, interfaceNames, 0, interfaceNames.length);
            for (int i = 0, l = this.objectInterfaceNames.length; i < l; i++)
                if (this.printConfigInfo)
                    CommonLog.info("{}.objectInterfaceNames[{}]={}", this.poolName, i, this.objectInterfaceNames[i]);
        }
        config.setObjectInterfaceNames(interfaceNames);
    }

    private Class[] loadObjectInterfaces() throws BeeObjectSourceConfigException {
        Class[] objectInterfaces = this.objectInterfaces;
        if (objectInterfaces == null && this.objectInterfaceNames != null) {
            objectInterfaces = new Class[this.objectInterfaceNames.length];
            for (int i = 0; i < this.objectInterfaceNames.length; i++) {
                try {
                    if (isBlank(this.objectInterfaceNames[i]))
                        throw new BeeObjectSourceConfigException("objectInterfaceNames[" + i + "]is empty or null");
                    objectInterfaces[i] = Class.forName(this.objectInterfaceNames[i]);
                } catch (ClassNotFoundException e) {
                    throw new BeeObjectSourceConfigException("Not found interface:" + this.objectInterfaceNames[i]);
                }
            }
        }

        if (objectInterfaces != null) {
            for (int i = 0, l = objectInterfaces.length; i < l; i++) {
                if (objectInterfaces[i] == null)
                    throw new BeeObjectSourceConfigException("interfaces array[" + i + "]is null");
                if (!objectInterfaces[i].isInterface())
                    throw new BeeObjectSourceConfigException("interfaces array[" + i + "]is not valid interface");
            }
        }
        return objectInterfaces;
    }

    private RawObjectFactory tryCreateObjectFactory(Class[] objectInterfaces) {
        //1: try to create factory from factory class/className
        Class objectFactoryClass = this.objectFactoryClass;
        if (objectFactoryClass == null && !isBlank(this.objectFactoryClassName)) {
            try {
                objectFactoryClass = Class.forName(this.objectFactoryClassName);
            } catch (ClassNotFoundException e) {
                throw new BeeObjectSourceConfigException("Not found object factory class:" + this.objectFactoryClassName);
            }
        }
        if (objectFactoryClass != null) {
            try {
                Constructor constructor = getClassConstructor(objectFactoryClass, RawObjectFactory.class, "object factory");
                RawObjectFactory factory = (RawObjectFactory) constructor.newInstance();
                setPropertiesValue(factory, this.factoryProperties);
                return factory;
            } catch (Throwable e) {
                throw new BeeObjectSourceConfigException("Failed to create object factory by class:" + objectFactoryClass.getName(), e);
            }
        }

        //2: try to create simple factory from object class/class name
        Class objectClass = this.objectClass;
        if (objectClass == null && !isBlank(this.objectClassName)) {
            try {
                objectClass = Class.forName(this.objectClassName);
            } catch (ClassNotFoundException e) {
                throw new BeeObjectSourceConfigException("Not found object class:" + this.objectClassName);
            }
        }

        if (objectClass != null) {
            return new SimpleObjectFactory(getClassConstructor(objectClass, objectInterfaces, "object class"));
        } else {
            throw new BeeObjectSourceConfigException("Must set value to one of ['objectFactoryClassName','objectClassName']");
        }
    }
}

