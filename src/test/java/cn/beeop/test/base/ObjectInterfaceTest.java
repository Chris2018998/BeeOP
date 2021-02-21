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
import cn.beeop.BeeObjectProxyHandle;
import cn.beeop.BeeObjectSource;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.test.Book;
import cn.beeop.test.JavaBookFactory;
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;

/**
 * ObjectFactory subclass
 *
 * @author chris.liao
 */
public class ObjectInterfaceTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setObjectFactoryClassName(JavaBookFactory.class.getName());
        config.setObjectInterfaces(new Class[]{Book.class});

        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        BeeObjectProxyHandle proxy = null;
        try {
            proxy = obs.getObject();
            if (proxy == null)
                TestUtil.assertError("Failed to get object");
            Book book = (Book) proxy;
        } catch (BeeObjectException e) {
        } finally {
            if (proxy != null)
                proxy.close();
        }
    }

}
