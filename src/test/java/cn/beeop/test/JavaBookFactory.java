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
package cn.beeop.test;

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

    public boolean isAlive(Object obj, long timeout) {
        return true;
    }
}
