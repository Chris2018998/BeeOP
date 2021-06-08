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
package cn.beeop.test.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.pool.PoolMonitorVo;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.JavaBook;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class ObjectHoldTimeoutTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setInitialSize(0);
        config.setHoldTimeout(1000);// hold and not using connection;
        config.setIdleCheckTimeInterval(1000L);//one second interval
        config.setDelayTimeForNextClear(1);
        config.setObjectClassName(JavaBook.class.getName());
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            PoolMonitorVo monitorVo = obs.getPoolMonitorVo();

            if (monitorVo.getIdleSize() + monitorVo.getUsingSize() != 1)
                TestUtil.assertError("Total connections not as expected 1");
            if (monitorVo.getUsingSize() != 1)
                TestUtil.assertError("Using connections not as expected 1");

            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
            if (monitorVo.getUsingSize() != 0)
                TestUtil.assertError("Using connections not as expected 0 after hold timeout");

            try {
                Object v = handle.call("toString", new Class[0], new Object[0]);
                System.out.println("handle isClosed:" + handle.isClosed());

                TestUtil.assertError("must throw closed exception");
            } catch (BeeObjectException e) {
                System.out.println(e);
            }

            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
        } finally {
            if (handle != null) handle.close();
        }
    }
}
