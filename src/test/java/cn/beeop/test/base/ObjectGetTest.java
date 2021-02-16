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
import cn.beeop.pool.ProxyObject;
import cn.beeop.test.BookFactory;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

public class ObjectGetTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setInitialSize(5);
        config.setIdleTimeout(3000);
        config.setObjectFactoryClassName(BookFactory.class.getName());
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        ProxyObject proxy = null;
        try {
            proxy = obs.getObject();
            if (proxy == null)
                TestUtil.assertError("Failed to get object");
        } catch (BeeObjectException e) {
        } finally {
            if (proxy != null)
                proxy.close();
        }
    }
}
