/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.config;

import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.pool.FastObjectPool;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.JavaBookFactory;

public class PassedConfigUnchangeableTest extends TestCase {
    private final int initSize = 5;
    private final int maxSize = 20;
    BeeObjectSourceConfig testConfig;
    private BeeObjectSource ds;

    public void setUp() throws Throwable {
        testConfig = new BeeObjectSourceConfig();
        testConfig.setObjectFactoryClassName(JavaBookFactory.class.getName());
        testConfig.setInitialSize(initSize);
        testConfig.setMaxActive(maxSize);
        testConfig.setIdleTimeout(3000);
        ds = new BeeObjectSource(testConfig);
    }

    public void tearDown() throws Throwable {
        ds.close();
    }

    public void test() throws Exception {
        testConfig.setInitialSize(10);
        testConfig.setMaxActive(50);

        FastObjectPool pool = (FastObjectPool) TestUtil.getFieldValue(ds, "pool");
        BeeObjectSourceConfig tempConfig = (BeeObjectSourceConfig) TestUtil.getFieldValue(pool, "poolConfig");
        if (tempConfig.getInitialSize() != initSize) TestUtil.assertError("initSize has changed,expected:" + initSize);
        if (tempConfig.getMaxActive() != maxSize) TestUtil.assertError("maxActive has changed,expected" + maxSize);
    }
}
