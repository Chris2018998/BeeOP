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

import static cn.beeop.pool.PoolStaticCenter.isBlank;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class PropertiesFileLoadTest extends TestCase {

    private static String checkBase(BeeObjectSourceConfig config) {
        if (!"Pool1".equals(config.getPoolName())) return "poolName error";
        if (!config.isFairMode()) return "fairMode error";
        if (config.getInitialSize() != 1) return "initialSize error";
        if (config.getMaxActive() != 10) return "maxActive error";
        if (config.getBorrowSemaphoreSize() != 4) return "borrowSemaphoreSize error";
        return null;
    }

    private static String checkTimeValue(BeeObjectSourceConfig config) {
        if (config.getMaxWait() != 8000) return "maxWait error";
        if (config.getIdleTimeout() != 18000) return "idleTimeout error";
        if (config.getHoldTimeout() != 30000) return "holdTimeout error";
        if (config.getValidTestTimeout() != 3) return "objectTestTimeout error";
        if (config.getValidAssumeTime() != 500) return "objectTestInterval error";
        if (config.getTimerCheckInterval() != 30000) return "idleCheckTimeInterval error";
        if (!config.isForceCloseUsingOnClear()) return "forceCloseUsingOnClear error";
        if (config.getDelayTimeForNextClear() != 3000) return "delayTimeForNextClear error";
        return null;
    }

    private static String checkInterface(BeeObjectSourceConfig config) {
        if (!"cn.beeop.test.object.JavaBook".equals(config.getObjectClassName())) return "objectClassName error";
        //if(!"cn.beeop.test.object.JavaBook".equalsString(config.getObjectClass().getClass().getName()))return"objectClass error";
        if (!"cn.beeop.test.object.JavaBookFactory".equals(config.getObjectFactoryClassName()))
            return "objectFactoryClassName error";
        if (!"cn.beeop.pool.FastObjectPool".equals(config.getPoolImplementClassName()))
            return "objectFactoryClassName error";
        if (!config.isEnableJmx()) return "enableJmx error";
        return null;
    }

    public void testConfig() {
        String filename = "beeop/PropertiesFileLoadTest.properties";
        URL url = PropertiesFileLoadTest.class.getClassLoader().getResource(filename);
        if (url == null) url = PropertiesFileLoadTest.class.getResource(filename);
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.loadFromPropertiesFile(url.getFile());

        String error = checkBase(config);
        if (!isBlank(error)) throw new BeeObjectSourceConfigException(error);
        error = checkTimeValue(config);
        if (!isBlank(error)) throw new BeeObjectSourceConfigException(error);
        error = checkInterface(config);
        if (!isBlank(error)) throw new BeeObjectSourceConfigException(error);
    }
}
