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
package cn.beeop;

import cn.beeop.pool.FastPool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

import static cn.beeop.pool.StaticCenter.isBlank;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Object pool configuration under object source
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class BeeObjectSourceConfig implements BeeObjectSourceConfigJmxBean {
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
    private long holdTimeout = MINUTES.toMillis(5);
    //seconds:max time to get check active result
    private int objectTestTimeout = 3;
    //milliseconds:object test interval time from last active time
    private long objectTestInterval = 500L;
    //milliseconds:interval time to run check task in scheduledThreadPoolExecutor
    private long idleCheckTimeInterval = MINUTES.toMillis(5);
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
    //pool implementation class name
    private String poolImplementClassName = FastPool.class.getName();
    //indicator,whether register pool to jmx
    private boolean enableJmx;

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
            if (maxActive > 1)
                this.borrowSemaphoreSize = Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors());
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
    public int getObjectTestTimeout() {
        return objectTestTimeout;
    }

    public void setObjectTestTimeout(int objectTestTimeout) {
        if (objectTestTimeout >= 0)
            this.objectTestTimeout = objectTestTimeout;
    }

    @Override
    public long getObjectTestInterval() {
        return objectTestInterval;
    }

    public void setObjectTestInterval(long objectTestInterval) {
        if (objectTestInterval >= 0)
            this.objectTestInterval = objectTestInterval;
    }

    @Override
    public long getIdleCheckTimeInterval() {
        return idleCheckTimeInterval;
    }

    public void setIdleCheckTimeInterval(long idleCheckTimeInterval) {
        if (idleCheckTimeInterval > 0)
            this.idleCheckTimeInterval = idleCheckTimeInterval;
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
        int len = (objectInterfaces != null) ? objectInterfaces.length : 0;
        Class[] interfaces = new Class[len];
        if (len > 0) System.arraycopy(objectInterfaces, 0, interfaces, 0, len);
        return interfaces;
    }

    public void setObjectInterfaces(Class[] interfaces) {
        int len = (interfaces != null) ? interfaces.length : 0;
        this.objectInterfaces = new Class[len];
        if (len > 0) System.arraycopy(interfaces, 0, objectInterfaces, 0, len);
    }

    public String[] getObjectInterfaceNames() {
        int len = (objectInterfaceNames != null) ? objectInterfaceNames.length : 0;
        String[] interfaceNames = new String[len];
        if (len > 0) System.arraycopy(objectInterfaceNames, 0, interfaceNames, 0, len);
        return interfaceNames;
    }

    public void setObjectInterfaceNames(String[] interfaceNames) {
        int len = (interfaceNames != null) ? interfaceNames.length : 0;
        this.objectInterfaceNames = new String[len];
        if (len > 0) System.arraycopy(interfaceNames, 0, objectInterfaceNames, 0, len);
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

    public void removeCreateProperties(String key) {
        createProperties.remove(key);
    }

    public void addCreateProperties(String key, Object value) {
        createProperties.put(key, value);
    }

    public Properties getCreateProperties() {
        return createProperties;
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

    void copyTo(BeeObjectSourceConfig config) {
        int modifiers;
        Field[] fields = BeeObjectSourceConfig.class.getDeclaredFields();
        for (Field field : fields) {
            modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers))
                continue;
            try {
                field.set(config, field.get(this));
            } catch (Exception e) {
                throw new BeeObjectSourceConfigException("Failed to copy field[" + field.getName() + "]", e);
            }
        }
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

        //1:load object implemented interfaces,if config
        Class[] objectInterfaces = loadObjectInterfaces();

        //2:try to create object factory
        BeeObjectFactory objectFactory = tryCreateObjectFactory();

        BeeObjectSourceConfig configCopy = new BeeObjectSourceConfig();
        this.copyTo(configCopy);
        configCopy.setObjectFactory(objectFactory);
        configCopy.setObjectInterfaces(objectInterfaces);

        if (!isBlank(password))
            configCopy.addCreateProperties("user", username);
        if (!isBlank(password))
            configCopy.addCreateProperties("password", password);
        if (!isBlank(serverUrl))
            configCopy.addCreateProperties("serverUrl", serverUrl);
        return configCopy;
    }

    public void loadPropertiesFile(String filename) throws IOException {
        if (isBlank(filename)) throw new IOException("Properties file can't be null");
        loadPropertiesFile(new File(filename));
    }

    public void loadPropertiesFile(File file) throws IOException {
        if (file == null) throw new IOException("Properties file can't be null");
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        if (!file.isFile()) throw new IOException("Target object is not a valid file");
        if (!file.getAbsolutePath().toLowerCase(Locale.US).endsWith(".properties"))
            throw new IOException("Target file is not a properties file");

        InputStream stream = null;
        try {
            stream = Files.newInputStream(Paths.get(file.toURI()));
            createProperties.clear();
            createProperties.load(stream);
        } finally {
            if (stream != null) stream.close();
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
            if (objectClass != null) return new ClassObjectFactory(objectClass);
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
                    return new ClassObjectFactory(objectClass);
                } catch (ClassNotFoundException e) {
                    throw new BeeObjectSourceConfigException("Not found object class:" + objectClassName);
                }
            } else {
                throw new BeeObjectSourceConfigException("objectClassName can't be null");
            }
        }
    }
}

