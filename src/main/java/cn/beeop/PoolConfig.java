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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

import static cn.beeop.StaticCenter.isBlank;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Connection pool configuration
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class PoolConfig {
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
    private int borrowSemaphoreSize;
    //* minutes:max idle time for pooledConnection(milliseconds),default value: three minutes
    private long idleTimeout = MINUTES.toMillis(3);
    //max hold time in Unused(milliseconds),pool will release it by forced
    private long holdTimeout = MINUTES.toMillis(5);
    //connection validate timeout:3 seconds
    private int connectionTestTimeout = 3;
    //milliseconds,max inactive time to check active for borrower
    private long connectionTestInterval = 500L;
    //close all connections in force when shutdown
    private boolean forceCloseConnection;
    //seconds,wait for retry to clear all connections
    private long waitTimeToClearPool = 3;
    //milliseconds,idle Check Time Period
    private long idleCheckTimeInterval = MINUTES.toMillis(5);
    //milliseconds,idle Check Time initialize delay
    private long idleCheckTimeInitDelay = SECONDS.toMillis(1);
    // Physical JDBC Connection factory class name
    private String connectionFactoryClassName;
    //Physical JDBC Connection factory
    private ObjectFactory connectionFactory;
    //connection extra properties
    private Properties connectProperties = new Properties();

    /**
     * enableJMX
     */
    private boolean enableJMX;

    public PoolConfig() {
        this(null, null, null, null);
    }

    public PoolConfig(String driver, String url, String user, String password) {

        //fix issue:#19 Chris-2020-08-16 begin
        borrowSemaphoreSize = Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors());
        //fix issue:#19 Chris-2020-08-16 end
    }

    void setAsChecked() {
        if (!this.checked)
            this.checked = true;
    }

    public long getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(long maxWait) {
        this.maxWait = maxWait;
    }


    public String getConnectionFactoryClassName() {
        return connectionFactoryClassName;
    }

    public void setConnectionFactoryClassName(String connectionFactoryClassName) {
        if (!this.checked && !isBlank(connectionFactoryClassName))
            this.connectionFactoryClassName = connectionFactoryClassName;
    }

    public ObjectFactory getObjectFactory() {
        return connectionFactory;
    }

    public void setObjectFactory(ObjectFactory connectionFactory) {
        if (!this.checked)
            this.connectionFactory = connectionFactory;
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
            //fix issue:#19 Chris-2020-08-16 begin
            if (maxActive > 1)
                this.borrowSemaphoreSize = Math.min(maxActive / 2, Runtime.getRuntime().availableProcessors());
            //fix issue:#19 Chris-2020-08-16 end
        }
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public long getHoldTimeout() {
        return holdTimeout;
    }

    public void setHoldTimeout(long holdTimeout) {
        this.holdTimeout = holdTimeout;
    }

    public int getBorrowSemaphoreSize() {
        return borrowSemaphoreSize;
    }

    public void setBorrowSemaphoreSize(int borrowSemaphoreSize) {
        if (!this.checked && borrowSemaphoreSize > 0)
            this.borrowSemaphoreSize = borrowSemaphoreSize;
    }

    public int getConnectionTestTimeout() {
        return connectionTestTimeout;
    }

    public void setConnectionTestTimeout(int connectionTestTimeout) {
        if (!this.checked && connectionTestTimeout > 0)
            this.connectionTestTimeout = connectionTestTimeout;
    }

    public long getConnectionTestInterval() {
        return connectionTestInterval;
    }

    public void setConnectionTestInterval(long connectionTestInterval) {
        if (!this.checked && connectionTestInterval > 0)
            this.connectionTestInterval = connectionTestInterval;
    }

    public boolean isForceCloseConnection() {
        return forceCloseConnection;
    }

    public void setForceCloseConnection(boolean forceCloseConnection) {
        if (!this.checked)
            this.forceCloseConnection = forceCloseConnection;
    }

    public long getWaitTimeToClearPool() {
        return waitTimeToClearPool;
    }

    public void setWaitTimeToClearPool(long waitTimeToClearPool) {
        if (!this.checked && waitTimeToClearPool >= 0)
            this.waitTimeToClearPool = waitTimeToClearPool;
    }

    public long getIdleCheckTimeInterval() {
        return idleCheckTimeInterval;
    }

    public void setIdleCheckTimeInterval(long idleCheckTimeInterval) {
        if (!this.checked && idleCheckTimeInterval >= 1000L)
            this.idleCheckTimeInterval = idleCheckTimeInterval;
    }

    public long getIdleCheckTimeInitDelay() {
        return idleCheckTimeInitDelay;
    }

    public void setIdleCheckTimeInitDelay(long idleCheckTimeInitDelay) {
        if (!this.checked && idleCheckTimeInitDelay >= 1000L)
            this.idleCheckTimeInitDelay = idleCheckTimeInitDelay;
    }

    public void removeConnectProperty(String key) {
        if (!this.checked) {
            connectProperties.remove(key);
        }
    }

    public void addConnectProperty(String key, String value) {
        if (!this.checked) {
            connectProperties.put(key, value);
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
        //fix issue:#19 Chris-2020-08-16 begin
        //if (this.borrowConcurrentSize > maxActive)
        //throw new ConfigException("Pool 'borrowConcurrentSize' must not be greater than pool max size");
        //fix issue:#19 Chris-2020-08-16 end

        if (this.idleTimeout <= 0)
            throw new ConfigException("Connection 'idleTimeout' must be greater than zero");
        if (this.holdTimeout <= 0)
            throw new ConfigException("Connection 'holdTimeout' must be greater than zero");
        if (this.maxWait <= 0)
            throw new ConfigException("Borrower 'maxWait' must be greater than zero");
    }

    private void setDataSourceProperty(String propName, Object propValue, Object bean) throws Exception {
        if (propName.endsWith(".")) return;
        int index = propName.lastIndexOf('.');
        if (index >= 0) propName = propName.substring(index + 1);

        propName = propName.trim();
        String methodName = "set" + propName.substring(0, 1).toUpperCase(Locale.US) + propName.substring(1);
        Method[] methods = bean.getClass().getMethods();
        Method targetMethod = null;
        for (Method method : methods) {
            if (method.getName().equals(methodName) && method.getParameterTypes().length == 1) {
                targetMethod = method;
                break;
            }
        }

        if (targetMethod != null) {
            Class paramType = targetMethod.getParameterTypes()[0];
            if (paramType.isInstance(propValue)) {
                targetMethod.invoke(bean, new Object[]{propValue});
            } else if (propValue instanceof String) {
                String value = (String) propValue;
                if (paramType == String.class) {
                    targetMethod.invoke(bean, new Object[]{propValue});
                } else if (paramType == boolean.class || paramType == Boolean.class) {
                    targetMethod.invoke(bean, new Object[]{Boolean.valueOf(value)});
                } else if (paramType == int.class || paramType == Integer.class) {
                    targetMethod.invoke(bean, new Object[]{Integer.valueOf(value)});
                } else if (paramType == long.class || paramType == Long.class) {
                    targetMethod.invoke(bean, new Object[]{Long.valueOf(value)});
                }
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
                connectProperties.clear();
                connectProperties.load(stream);
            } finally {
                if (stream != null) stream.close();
            }
        }
    }
}

