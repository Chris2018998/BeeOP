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
package cn.beeop.test.config;

import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.BeeObjectSourceConfigException;
import cn.beeop.test.TestCase;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Properties;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class PropertiesFileLoadTest extends TestCase {
    public void test() throws Exception {
        String filename= "PropertiesFileLoadTest.properties";
        URL url =PropertiesFileLoadTest.class.getClassLoader().getResource(filename);
        if (url == null) url = PropertiesFileLoadTest.class.getResource(filename);

        BeeObjectSourceConfig testConfig = new BeeObjectSourceConfig();
        testConfig.loadFromPropertiesFile(url.getFile());

        if(!"root".equals(testConfig.getUsername()))throw new BeeObjectSourceConfigException("username error");
        if(!"root".equals(testConfig.getPassword()))throw new BeeObjectSourceConfigException("password error");
        if(!"http://www.xxx".equals(testConfig.getServerUrl()))throw new BeeObjectSourceConfigException("jdbcUrl error");

        if(!"Pool1".equals(testConfig.getPoolName()))throw new BeeObjectSourceConfigException("poolName error");
        if(!testConfig.isFairMode())throw new BeeObjectSourceConfigException("fairMode error");
        if(testConfig.getInitialSize()!=1)throw new BeeObjectSourceConfigException("initialSize error");
        if(testConfig.getMaxActive()!=10)throw new BeeObjectSourceConfigException("maxActive error");
        if(testConfig.getBorrowSemaphoreSize()!=4)throw new BeeObjectSourceConfigException("borrowSemaphoreSize error");
        if(testConfig.getMaxWait()!=8000)throw new BeeObjectSourceConfigException("maxWait error");
        if(testConfig.getIdleTimeout()!=18000)throw new BeeObjectSourceConfigException("idleTimeout error");
        if(testConfig.getHoldTimeout()!=30000)throw new BeeObjectSourceConfigException("holdTimeout error");
        if(testConfig.getObjectTestTimeout()!=3)throw new BeeObjectSourceConfigException("objectTestTimeout error");
        if(testConfig.getObjectTestInterval()!=500)throw new BeeObjectSourceConfigException("objectTestInterval error");
        if(testConfig.getIdleCheckTimeInterval()!=30000)throw new BeeObjectSourceConfigException("idleCheckTimeInterval error");
        if(!testConfig.isForceCloseUsingOnClear())throw new BeeObjectSourceConfigException("forceCloseUsingOnClear error");
        if(testConfig.getDelayTimeForNextClear()!=3000)throw new BeeObjectSourceConfigException("delayTimeForNextClear error");

        if(!"cn.beeop.test.object.JavaBook".equals(testConfig.getObjectClassName()))throw new BeeObjectSourceConfigException("objectClassName error");
        //if(!"cn.beeop.test.object.JavaBook".equals(testConfig.getObjectClass().getClass().getName()))throw new BeeObjectSourceConfigException("objectClass error");
        if(!"cn.beeop.test.object.JavaBookFactory".equals(testConfig.getObjectFactoryClassName()))throw new BeeObjectSourceConfigException("objectFactoryClassName error");
        if(!"cn.beeop.test.object.JavaBookFactory".equals(testConfig.getObjectFactory().getClass().getName()))throw new BeeObjectSourceConfigException("objectFactory error");
        if(!"cn.beeop.pool.FastPool".equals(testConfig.getPoolImplementClassName()))throw new BeeObjectSourceConfigException("xaConnectionFactoryClassName error");
        if(!testConfig.isEnableJmx())throw new BeeObjectSourceConfigException("enableJmx error");

        Field connectPropertiesField = BeeObjectSourceConfig.class.getDeclaredField("createProperties");
        connectPropertiesField.setAccessible(true);
        Properties connectProperties=(Properties)connectPropertiesField.get(testConfig);
        if(!"true".equals(connectProperties.getProperty("cachePrepStmts")))
            throw new BeeObjectSourceConfigException("connectProperties error");
        if(!"50".equals(connectProperties.getProperty("prepStmtCacheSize")))
            throw new BeeObjectSourceConfigException("connectProperties error");
    }
}
