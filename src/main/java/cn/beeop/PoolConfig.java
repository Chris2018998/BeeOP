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

import static cn.beeop.StaticCenter.isBlank;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * object pool configuration
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class PoolConfig implements PoolConfigJmx {
    //borrower request timeout(milliseconds)
    protected long maxWait = SECONDS.toMillis(8);
    //indicator to not allow to modify configuration after initialization
    private boolean checked;
    //pool name
    private String poolName;
    //two mode:fair or compete
    private boolean fairMode;
    //pool initialization size
    private int initialSize;
    //pool allow max size
    private int maxActive = 10;
    //borrow Semaphore Size
    private int borrowSemaphoreSize = Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors());
    //* minutes:max idle time for pooledConnection(milliseconds),default value: three minutes
    private long idleTimeout = MINUTES.toMillis(3);
    //max hold time in Unused(milliseconds),pool will release it by forced
    private long holdTimeout = MINUTES.toMillis(5);
    //object validate timeout:3 seconds
    private int aliveTestTimeout = 3;
    //milliseconds,max inactive time to check object active
    private long aliveTestInterval = 500L;
    //close all connections in force when shutdown
    private boolean forceClose;
    //seconds,wait for retry to clear all connections
    private long waitTimeToClearPool = 3;
    //milliseconds,idle Check Time Period
    private long idleCheckTimeInterval = MINUTES.toMillis(5);

    //object factory
    private ObjectFactory objectFactory;
    //object factory class name
    private String objectFactoryClassName;
    //object creating properties
    private Properties createProperties = new Properties();
    //enableJMXe
    private boolean enableJMX;

    public boolean isChecked() {
        return checked;
    }

    void setAsChecked() {
        if (!this.checked)
            this.checked = true;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName1) {
        if (!this.checked && !isBlank(poolName1))
            this.poolName = poolName1;
    }

    public boolean isFairMode() {
        return fairMode;
    }

    public void setFairMode(boolean fairMode) {
        if (!this.checked)
            this.fairMode = fairMode;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(int initialSize) {
        if (!this.checked && initialSize > 0)
            this.initialSize = initialSize;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        if (!this.checked && maxActive > 0) {
            this.maxActive = maxActive;
            if (maxActive > 1)
                this.borrowSemaphoreSize = Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors());
        }
    }

    public int getBorrowSemaphoreSize() {
        return borrowSemaphoreSize;
    }

    public void setBorrowSemaphoreSize(int borrowSemaphoreSize) {
        if (!this.checked && borrowSemaphoreSize > 0)
            this.borrowSemaphoreSize = borrowSemaphoreSize;
    }

    public long getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(long maxWait) {
        if (!this.checked && maxWait > 0)
            this.maxWait = maxWait;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        if (!this.checked && idleTimeout > 0)
            this.idleTimeout = idleTimeout;
    }

    public long getHoldTimeout() {
        return holdTimeout;
    }

    public void setHoldTimeout(long holdTimeout) {
        if (!this.checked && holdTimeout > 0)
            this.holdTimeout = holdTimeout;
    }

    public int getAliveTestTimeout() {
        return aliveTestTimeout;
    }

    public void setAliveTestTimeout(int aliveTestTimeout) {
        if (!this.checked && aliveTestTimeout > 0)
            this.aliveTestTimeout = aliveTestTimeout;
    }

    public long getAliveTestInterval() {
        return aliveTestInterval;
    }

    public void setAliveTestInterval(long aliveTestInterval) {
        if (!this.checked && aliveTestInterval > 0)
            this.aliveTestInterval = aliveTestInterval;
    }

    public boolean isForceClose() {
        return forceClose;
    }

    public void setForceClose(boolean forceClose) {
        if (!this.checked)
            this.forceClose = forceClose;
    }

    public long getWaitTimeToClearPool() {
        return waitTimeToClearPool;
    }

    public void setWaitTimeToClearPool(long waitTimeToClearPool) {
        if (!this.checked && waitTimeToClearPool > 0)
            this.waitTimeToClearPool = waitTimeToClearPool;
    }

    public long getIdleCheckTimeInterval() {
        return idleCheckTimeInterval;
    }

    public void setIdleCheckTimeInterval(long idleCheckTimeInterval) {
        if (!this.checked && idleCheckTimeInterval > 0)
            this.idleCheckTimeInterval = idleCheckTimeInterval;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public void setObjectFactory(ObjectFactory objectFactory) {
        if (!this.checked && objectFactory != null)
            this.objectFactory = objectFactory;
    }

    public String getObjectFactoryClassName() {
        return objectFactoryClassName;
    }

    public void setObjectFactoryClassName(String objectFactoryClassName) {
        if (!this.checked && !isBlank(objectFactoryClassName))
            this.objectFactoryClassName = objectFactoryClassName;
    }

    Properties getCreateProperties() {
        return createProperties;
    }

    public void removeProperty(String key) {
        if (!this.checked) {
            createProperties.remove(key);
        }
    }

    public void addProperty(String key, String value) {
        if (!this.checked) {
            createProperties.put(key, value);
        }
    }

    public boolean isEnableJMX() {
        return enableJMX;
    }

    public void setEnableJMX(boolean enableJMX) {
        if (!this.checked)
            this.enableJMX = enableJMX;
    }

    void copyTo(PoolConfig config) throws ConfigException {
        int modifiers;
        Field[] fields = ConfigException.class.getDeclaredFields();
        for (Field field : fields) {
            if ("checked".equals(field.getName())) continue;
            modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers))
                continue;
            try {
                field.set(config, field.get(this));
            } catch (Exception e) {
                throw new ConfigException("Failed to copy field[" + field.getName() + "]", e);
            }
        }
    }

    //check pool configuration
    void check() throws ConfigException {
        if (this.maxActive <= 0)
            throw new ConfigException("Pool 'maxActive' must be greater than zero");
        if (this.initialSize < 0)
            throw new ConfigException("Pool 'initialSize' must be greater than zero");
        if (this.initialSize > maxActive)
            throw new ConfigException("Pool 'initialSize' must not be greater than 'maxActive'");
        if (this.borrowSemaphoreSize <= 0)
            throw new ConfigException("Pool 'borrowSemaphoreSize' must be greater than zero");

        if (this.idleTimeout <= 0)
            throw new ConfigException("Object 'idleTimeout' must be greater than zero");
        if (this.holdTimeout <= 0)
            throw new ConfigException("Object 'holdTimeout' must be greater than zero");
        if (this.maxWait <= 0)
            throw new ConfigException("Borrower 'maxWait' must be greater than zero");
        if (this.objectFactory == null && isBlank(objectFactoryClassName))
            throw new ConfigException("must provide one of them:'objectFactory' or 'objectFactoryClassName'");

        if (objectFactory == null && !isBlank(this.objectFactoryClassName)) {
            try {
                Class<?> conFactClass = Class.forName(objectFactoryClassName, true, PoolConfig.class.getClassLoader());
                if (ObjectFactory.class.isAssignableFrom(conFactClass)) {
                    objectFactory = (ObjectFactory) conFactClass.newInstance();
                } else {
                    throw new ConfigException("Custom connection factory class must be implemented 'ObjectFactory' interface");
                }
            } catch (ClassNotFoundException e) {
                throw new ConfigException("Class(" + objectFactoryClassName + ")not found ", e);
            } catch (InstantiationException e) {
                throw new ConfigException("Failed to instantiate connection factory class:" + objectFactoryClassName, e);
            } catch (IllegalAccessException e) {
                throw new ConfigException("Failed to instantiate connection factory class:" + objectFactoryClassName, e);
            }
        }
    }

    public void loadPropertiesFile(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) throw new FileNotFoundException(filename);
        loadPropertiesFile(file);
    }

    public void loadPropertiesFile(File file) throws IOException {
        if (!file.isFile()) throw new IOException("Invalid properties file");
        if (!file.getAbsolutePath().toLowerCase(Locale.US).endsWith(".properties"))
            throw new IOException("Invalid properties file");

        if (!checked) {
            InputStream stream = null;
            try {
                stream = Files.newInputStream(Paths.get(file.toURI()));
                createProperties.clear();
                createProperties.load(stream);
            } finally {
                if (stream != null) stream.close();
            }
        }
    }
}