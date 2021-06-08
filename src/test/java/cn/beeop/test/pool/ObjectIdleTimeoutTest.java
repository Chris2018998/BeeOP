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
public class ObjectIdleTimeoutTest extends TestCase {
    private BeeObjectSource obs;
    private int initSize = 5;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setInitialSize(initSize);
        config.setMaxActive(initSize);
        config.setIdleTimeout(1000);
        config.setIdleCheckTimeInterval(1000L);// two seconds interval
        config.setDelayTimeForNextClear(1);
        config.setObjectClassName(JavaBook.class.getName());
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        PoolMonitorVo monitorVo = obs.getPoolMonitorVo();
        int usingSize = monitorVo.getUsingSize();
        int idleSize = monitorVo.getIdleSize();
        int totalSize = usingSize + idleSize;

        if (totalSize != initSize)
            TestUtil.assertError("Total object not as expected:" + initSize);
        if (idleSize != initSize) TestUtil.assertError("Idle object not as expected:" + initSize);

        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
        monitorVo = obs.getPoolMonitorVo();
        usingSize = monitorVo.getUsingSize();
        idleSize = monitorVo.getIdleSize();
        totalSize = usingSize + idleSize;

        if (totalSize != 0)
            TestUtil.assertError("Total size not expected:" + 0);
        if (idleSize != 0) TestUtil.assertError("Idle size not expected:" + 0);
    }
}
