/**
 * Copyright 2012 Akiban Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.persistit.stress;

import com.persistit.Persistit;
import com.persistit.Transaction.CommitPolicy;
import com.persistit.stress.unit.StressRecovery;

public class Recovery2 extends AbstractSuite {

    static String name() {
        return Recovery2.class.getSimpleName();
    }

    public static void main(final String[] args) throws Exception {
        new Recovery2(args).runTest();
    }

    public Recovery2(final String[] args) {
        super(name(), args);
    }

    @Override
    public void runTest() throws Exception {

        // Don't delete persistit data here!

        add(new StressRecovery(substitute("verify=$datapath$/recovery.log latency=1000")));

        final Persistit persistit = makePersistit(16384, "64M,1T,256M,0.7", CommitPolicy.SOFT);
        persistit.getManagement().setAppendOnly(true);

        try {
            execute(persistit);
        } finally {
            persistit.close();
        }
    }
}
