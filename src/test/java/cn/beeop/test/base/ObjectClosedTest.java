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
import cn.beeop.test.object.JavaBookFactory;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

public class ObjectClosedTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setInitialSize(5);
        config.setIdleTimeout(3000);
        config.setObjectFactory(new JavaBookFactory());
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            handle.close();
            handle.call("toString", new Class[0], new Object[0]);
            TestUtil.assertError("Closed test failed");
        } catch (BeeObjectException e) {
        } finally {
            if (handle != null)
                handle.close();
        }
    }
}
