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
package cn.beeop.test.config;

import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.pool.FastPool;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.JavaBookFactory;

public class PassedConfigUnchangeableTest extends TestCase {
    BeeObjectSourceConfig testConfig;
    private BeeObjectSource ds;
    private int initSize = 5;
    private int maxSize = 20;

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

    public void test() throws InterruptedException, Exception {
        testConfig.setInitialSize(10);
        testConfig.setMaxActive(50);

        FastPool pool = (FastPool) TestUtil.getFieldValue(ds, "pool");
        BeeObjectSourceConfig tempConfig = (BeeObjectSourceConfig) TestUtil.getFieldValue(pool, "poolConfig");
        if (tempConfig.getInitialSize() != initSize) TestUtil.assertError("initSize has changed,expected:" + initSize);
        if (tempConfig.getMaxActive() != maxSize) TestUtil.assertError("maxActive has changed,expected" + maxSize);
    }
}
