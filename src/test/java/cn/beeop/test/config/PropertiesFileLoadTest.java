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

    private static void checkBase(BeeObjectSourceConfig config) {
        if (!"Pool1".equals(config.getPoolName())) throw new BeeObjectSourceConfigException("poolName error");
        if (!config.isFairMode()) throw new BeeObjectSourceConfigException("fairMode error");
        if (config.getInitialSize() != 1) throw new BeeObjectSourceConfigException("initialSize error");
        if (config.getMaxActive() != 10) throw new BeeObjectSourceConfigException("maxActive error");
        if (config.getBorrowSemaphoreSize() != 4) throw new BeeObjectSourceConfigException("borrowSemaphoreSize error");
    }

    private static void checkTimeValue(BeeObjectSourceConfig config) {
        if (config.getMaxWait() != 8000) throw new BeeObjectSourceConfigException("maxWait error");
        if (config.getIdleTimeout() != 18000) throw new BeeObjectSourceConfigException("idleTimeout error");
        if (config.getHoldTimeout() != 30000) throw new BeeObjectSourceConfigException("holdTimeout error");
        if (config.getValidTestTimeout() != 3) throw new BeeObjectSourceConfigException("objectTestTimeout error");
        if (config.getValidAssumeTime() != 500)
            throw new BeeObjectSourceConfigException("objectTestInterval error");
        if (config.getTimerCheckInterval() != 30000)
            throw new BeeObjectSourceConfigException("idleCheckTimeInterval error");
        if (!config.isForceCloseUsingOnClear())
            throw new BeeObjectSourceConfigException("forceCloseUsingOnClear error");
        if (config.getDelayTimeForNextClear() != 3000)
            throw new BeeObjectSourceConfigException("delayTimeForNextClear error");
    }

    private static void checkInterface(BeeObjectSourceConfig config) {
        if (!"cn.beeop.test.object.JavaBook".equals(config.getObjectClassName()))
            throw new BeeObjectSourceConfigException("objectClassName error");
        //if(!"cn.beeop.test.object.JavaBook".equalsString(config.getObjectClass().getClass().getName()))throw new BeeObjectSourceConfigException("objectClass error");
        if (!"cn.beeop.test.object.JavaBookFactory".equals(config.getObjectFactoryClassName()))
            throw new BeeObjectSourceConfigException("objectFactoryClassName error");
        if (!"cn.beeop.pool.FastObjectPool".equals(config.getPoolImplementClassName()))
            throw new BeeObjectSourceConfigException("objectFactoryClassName error");
        if (!config.isEnableJmx()) throw new BeeObjectSourceConfigException("enableJmx error");
    }

    public void testConfig() {
        String filename = "beeop/PropertiesFileLoadTest.properties";
        URL url = PropertiesFileLoadTest.class.getClassLoader().getResource(filename);
        if (url == null) url = PropertiesFileLoadTest.class.getResource(filename);
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.loadFromPropertiesFile(url.getFile());

        checkBase(config);
        checkTimeValue(config);
        checkInterface(config);
    }
}
