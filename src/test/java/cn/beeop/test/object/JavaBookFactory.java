/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.object;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectFactory;

import java.util.Properties;

/**
 * ObjectFactory subclass
 *
 * @author chris.liao
 */
public class JavaBookFactory implements BeeObjectFactory {
    public Object create(Properties prop) throws BeeObjectException {
        return new JavaBook("Java核心技术·卷1", System.currentTimeMillis());
    }

    public void setDefault(Object obj) throws BeeObjectException {
    }

    public void reset(Object obj) throws BeeObjectException {
    }

    public void destroy(Object obj) {
    }

    public boolean isValid(Object obj, int timeout) {
        return true;
    }
}
