/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.config;

import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.BeeObjectSourceConfigException;
import cn.beeop.test.TestCase;

import java.net.URL;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class PropertiesFileLoadTest extends TestCase {
    public void test() throws Exception {
        String filename = "beeop/PropertiesFileLoadTest.properties";
        URL url = PropertiesFileLoadTest.class.getClassLoader().getResource(filename);
        if (url == null) url = PropertiesFileLoadTest.class.getResource(filename);

        BeeObjectSourceConfig testConfig = new BeeObjectSourceConfig();
        testConfig.loadFromPropertiesFile(url.getFile());

        if (!"Pool1".equals(testConfig.getPoolName())) throw new BeeObjectSourceConfigException("poolName error");
        if (!testConfig.isFairMode()) throw new BeeObjectSourceConfigException("fairMode error");
        if (testConfig.getInitialSize() != 1) throw new BeeObjectSourceConfigException("initialSize error");
        if (testConfig.getMaxActive() != 10) throw new BeeObjectSourceConfigException("maxActive error");
        if (testConfig.getBorrowSemaphoreSize() != 4)
            throw new BeeObjectSourceConfigException("borrowSemaphoreSize error");
        if (testConfig.getMaxWait() != 8000) throw new BeeObjectSourceConfigException("maxWait error");
        if (testConfig.getIdleTimeout() != 18000) throw new BeeObjectSourceConfigException("idleTimeout error");
        if (testConfig.getHoldTimeout() != 30000) throw new BeeObjectSourceConfigException("holdTimeout error");
        if (testConfig.getValidTestTimeout() != 3) throw new BeeObjectSourceConfigException("objectTestTimeout error");
        if (testConfig.getValidAssumeTime() != 500)
            throw new BeeObjectSourceConfigException("objectTestInterval error");
        if (testConfig.getTimerCheckInterval() != 30000)
            throw new BeeObjectSourceConfigException("idleCheckTimeInterval error");
        if (!testConfig.isForceCloseUsingOnClear())
            throw new BeeObjectSourceConfigException("forceCloseUsingOnClear error");
        if (testConfig.getDelayTimeForNextClear() != 3000)
            throw new BeeObjectSourceConfigException("delayTimeForNextClear error");

        if (!"cn.beeop.test.object.JavaBook".equals(testConfig.getObjectClassName()))
            throw new BeeObjectSourceConfigException("objectClassName error");
        //if(!"cn.beeop.test.object.JavaBook".equalsString(testConfig.getObjectClass().getClass().getName()))throw new BeeObjectSourceConfigException("objectClass error");
        if (!"cn.beeop.test.object.JavaBookFactory".equals(testConfig.getObjectFactoryClassName()))
            throw new BeeObjectSourceConfigException("objectFactoryClassName error");
        if (!"cn.beeop.pool.FastObjectPool".equals(testConfig.getPoolImplementClassName()))
            throw new BeeObjectSourceConfigException("xaConnectionFactoryClassName error");
        if (!testConfig.isEnableJmx()) throw new BeeObjectSourceConfigException("enableJmx error");

    }
}
