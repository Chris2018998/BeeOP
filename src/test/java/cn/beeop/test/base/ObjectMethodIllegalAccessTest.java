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
import cn.beeop.test.TestCase;
import cn.beeop.test.TestUtil;
import cn.beeop.test.object.Book;
import cn.beeop.test.object.JavaBook;

import java.lang.reflect.UndeclaredThrowableException;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class ObjectMethodIllegalAccessTest extends TestCase {
    private BeeObjectSource obs;

    public void setUp() throws Throwable {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setObjectClass(JavaBook.class);
        config.setObjectInterfaces(new Class[]{Book.class});
        config.addExcludeMethodName("getName");
        obs = new BeeObjectSource(config);
    }

    public void tearDown() throws Throwable {
        obs.close();
    }

    public void test() throws InterruptedException, Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            test1(handle);
            test2(handle);
        } catch (BeeObjectException e) {
        } finally {
            if (handle != null)
                handle.close();
        }
    }

    public void test1(BeeObjectHandle handle) throws Exception {
        try {
            handle.call("getName", new Class[0], new Object[0]);
            TestUtil.assertError("Object method illegal access test fail");
        } catch (BeeObjectException e) {
            System.out.println("Handle method illegal access test OK");
        }
    }

    public void test2(BeeObjectHandle handle) throws Exception {
        try {
            Book book = (Book) handle.getProxyObject();
            System.out.println(book.getName());
            TestUtil.assertError("Proxy method illegal access test fail");
        } catch (UndeclaredThrowableException e) {
            if (e.getCause() instanceof BeeObjectException)
                System.out.println("Proxy method illegal access test OK");
            else
                TestUtil.assertError("Proxy method illegal access test fail");
        }
    }
}
