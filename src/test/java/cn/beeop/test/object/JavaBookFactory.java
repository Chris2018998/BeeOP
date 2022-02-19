/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.test.object;

import cn.beeop.RawObjectFactory;
import cn.beeop.pool.ObjectException;

/**
 * ObjectFactory subclass
 *
 * @author chris.liao
 */
public class JavaBookFactory implements RawObjectFactory {
    public Object create() throws ObjectException {
        return new JavaBook("Java核心技术·卷1", System.currentTimeMillis());
    }

    public void setDefault(Object obj) throws ObjectException {
    }

    public void reset(Object obj) throws ObjectException {
    }

    public void destroy(Object obj) {
    }

    public boolean isValid(Object obj, int timeout) {
        return true;
    }
}
