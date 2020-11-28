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

import cn.beeop.ObjectPool;
import cn.beeop.PoolConfig;
import cn.beeop.ProxyObject;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

public class ObjectGetTest extends TestCase {
    private ObjectPool pool;

    public void setUp() throws Throwable {
        PoolConfig config = new PoolConfig();
        pool = new ObjectPool(config);
    }

    public void tearDown() throws Throwable {
        pool.close();
    }

    public void test() throws InterruptedException, Exception {
        ProxyObject proxy = null;
        try {
            proxy = pool.getObject();
            if (proxy == null)
                TestUtil.assertError("Failed to get object");
        } finally {
             if(proxy!=null)proxy.close();
        }
    }
}
