/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.JavaBookFactory;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class ObjectFactoryNameTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setObjectFactoryClassName(JavaBookFactory.class.getName());
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            if (handle == null)
                TestUtil.assertError("Failed to get object");
        } catch (BeeObjectException e) {
        } finally {
            if (handle != null)
                handle.close();
        }
    }
}
