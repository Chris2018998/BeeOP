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
package cn.beeop.test.base;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.pool.ObjectHandle;
import cn.beeop.test.JavaBook;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

import java.util.concurrent.CountDownLatch;

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

    public void test() throws InterruptedException, Exception {
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
        BeeObjectException e = null;
        CountDownLatch lacth;

        TestThread(CountDownLatch lacth) {
            this.lacth = lacth;
        }

        public void run() {
            ObjectHandle proxy = null;
            try {
                proxy = (ObjectHandle) obs.getObject();
            } catch (BeeObjectException e) {
                this.e = e;
            } finally {
                if (proxy != null)
                    try {
                        proxy.close();
                    } catch (BeeObjectException e1) {
                        e1.printStackTrace();
                    }
            }
            lacth.countDown();
        }
    }
}
