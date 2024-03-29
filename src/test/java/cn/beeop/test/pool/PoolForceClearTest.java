/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.pool;

import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.JavaBook;

import java.util.concurrent.TimeUnit;

public class PoolForceClearTest extends TestCase {
    private final int initSize = 10;
    private final long delayTimeForNextClear = TimeUnit.SECONDS.toMillis(10);//10 Seconds
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setInitialSize(initSize);
        config.setMaxActive(initSize);
        config.setObjectClass(JavaBook.class);
        config.setDelayTimeForNextClear(delayTimeForNextClear);//Ms
        obs = new BeeObjectSource(config);
    }

    public void testForceClear() throws Throwable {
        long time1 = System.currentTimeMillis();
        obs.clear(true);
        long tookTime = System.currentTimeMillis() - time1;
        if (tookTime > delayTimeForNextClear) {
            TestUtil.assertError("Pool force clear test failed");
        } else {
            System.out.println("Pool force clear time: " + tookTime + "ms");
        }
    }

    public void tearDown() throws Throwable {
        obs.close();
    }
}
