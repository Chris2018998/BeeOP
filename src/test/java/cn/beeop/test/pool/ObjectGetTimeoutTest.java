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
import cn.beeop.pool.ObjectHandle;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.JavaBook;

import java.util.concurrent.CountDownLatch;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class ObjectGetTimeoutTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setMaxActive(1);
        config.setMaxWait(3000);
        config.setObjectClass(JavaBook.class);
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            CountDownLatch lacth = new CountDownLatch(1);
            TestThread testTh = new TestThread(lacth);
            testTh.start();

            lacth.await();
            if (testTh.e == null)
                TestUtil.assertError("Object get timeout");
            else
                System.out.println(testTh.e);
        } finally {
            if (handle != null)
                handle.close();
        }
    }

    class TestThread extends Thread {
        Exception e = null;
        CountDownLatch lacth;

        TestThread(CountDownLatch lacth) {
            this.lacth = lacth;
        }

        public void run() {
            ObjectHandle proxy = null;
            try {
                proxy = (ObjectHandle) obs.getObject();
            } catch (Exception e) {
                this.e = e;
            } finally {
                if (proxy != null)
                    try {
                        proxy.close();
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
            }
            lacth.countDown();
        }
    }
}
