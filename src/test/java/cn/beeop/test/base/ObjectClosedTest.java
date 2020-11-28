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

import cn.beeop.ObjectException;
import cn.beeop.ObjectPool;
import cn.beeop.PoolConfig;
import cn.beeop.ProxyObject;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class ObjectClosedTest extends TestCase {
    private ObjectPool pool;

    public void setUp() throws Throwable {
        PoolConfig config = new PoolConfig();
        config.setInitialSize(5);
        config.setIdleTimeout(3000);
        pool = new ObjectPool(config);
    }

    public void tearDown() throws Throwable {
        pool.close();
    }

    public void test() throws InterruptedException, Exception {
        ProxyObject proxy = null;
        try {
            proxy = pool.getObject();
            proxy.close();
            proxy.call("toString", new Class[0], new Object[0]);
            TestUtil.assertError("Closed test failed");
        }catch(ObjectException e){
        } finally {
            if (proxy != null)
                proxy.close();
        }
    }
}
