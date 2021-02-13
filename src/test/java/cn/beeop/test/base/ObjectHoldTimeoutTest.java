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
import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.pool.PoolMonitorVo;
import cn.beeop.pool.ProxyObject;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

public class ObjectHoldTimeoutTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setInitialSize(0);
        config.setHoldTimeout(1000);// hold and not using connection;
        config.setIdleCheckTimeInterval(1000L);// two seconds interval
        config.setDelayTimeForNextClear(1);
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        ProxyObject proxy = null;
        try {
            proxy = obs.getObject();
            PoolMonitorVo monitorVo = obs.getPoolMonitorVo();

            if (monitorVo.getIdleSize() + monitorVo.getUsingSize() != 1)
                TestUtil.assertError("Total connections not as expected 1");
            if (monitorVo.getUsingSize() != 1)
                TestUtil.assertError("Using connections not as expected 1");

            Thread.sleep(4000);
            if (monitorVo.getUsingSize() != 0)
                TestUtil.assertError("Using connections not as expected 0 after hold timeout");

            try {
                proxy.call("toString", new Class[0], new Object[0]);
                TestUtil.assertError("must throw closed exception");
            } catch (BeeObjectException e) {
                System.out.println(e);
            }

            Thread.sleep(4000);
        } finally {
            if (proxy != null) proxy.close();
        }
    }
}
