/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Test case pool
 *
 * @author chris.liao
 */
public class TestCase {
    public void setUp() throws Throwable {
    }

    public void tearDown() throws Throwable {
    }

    void run() throws Throwable {
        try {
            setUp();
            runTest();
        } finally {
            try {
                tearDown();
            } catch (Throwable e) {
            }
        }
    }

    private void runTest() throws Throwable {
        int successCount = 0, failedCount = 0;
        long beginTime = System.currentTimeMillis();
        Method[] methods = this.getClass().getMethods();
        System.out.println("Case[" + this.getClass().getName() + "]begin");

        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().startsWith("test") && methods[i].getParameterTypes().length == 0) {
                try {
                    methods[i].invoke(this);
                    successCount++;
                } catch (Throwable e) {
                    failedCount++;
                    System.out.println("Failed to run test method:" + methods[i].getName() + " in Class[" + this.getClass().getName() + "]");
                    if (e instanceof InvocationTargetException) {
                        ((InvocationTargetException) e).getTargetException().printStackTrace();
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("took time:" + (endTime - beginTime) + "ms,sucessed(" + successCount + "),failed(" + failedCount + ")");
        if (failedCount > 0) throw new Exception("Failed in Case[" + this.getClass().getName() + "]");
    }
}
