/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.pool;

import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * ObjectFactory subclass
 *
 * @author chris.liao
 */
public class ObjectInterfaceTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
       // config.setObjectFactoryClassName(JavaBookFactory.class.getName());
       // config.setObjectInterfaces(new Class[]{Book.class});
        config.setObjectClass(HashMap.class);
        config.setObjectInterfaces(new Class[]{Map.class});
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            if (handle == null)
                TestUtil.assertError("Failed to get object");
            Map book = (Map) handle.getObjectProxy();
            System.out.println("Book name:" + book.size());
        } finally {
            if (handle != null)
                handle.close();
        }
    }

}
