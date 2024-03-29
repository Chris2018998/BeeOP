/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.pool;

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

    public void test() throws Exception {
        BeeObjectHandle handle = null;
        try {
            handle = obs.getObject();
            test1(handle);
            test2(handle);
        } finally {
            if (handle != null)
                handle.close();
        }
    }

    public void test1(BeeObjectHandle handle) throws Exception {
        try {
            handle.call("getName", new Class[0], new Object[0]);
            TestUtil.assertError("Object method illegal access test fail");
        } catch (Exception e) {
            System.out.println("Handle method illegal access test OK");
        }
    }

    public void test2(BeeObjectHandle handle) throws Exception {
        try {
            Book book = (Book) handle.getObjectProxy();
            System.out.println(book.getName());
            TestUtil.assertError("Proxy method illegal access test fail");
        } catch (UndeclaredThrowableException e) {
            if (e.getCause() instanceof Exception)
                System.out.println("Proxy method illegal access test OK");
            else
                TestUtil.assertError("Proxy method illegal access test fail");
        }
    }
}
