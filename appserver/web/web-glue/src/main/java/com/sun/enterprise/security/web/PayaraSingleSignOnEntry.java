/*
 *
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) 2022 Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 *
 */

package com.sun.enterprise.security.web;

import org.apache.catalina.authenticator.SingleSignOnEntry;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicLong;

public class PayaraSingleSignOnEntry extends SingleSignOnEntry {

    protected final String id;
    protected final String realmName;
    protected long lastAccessTime;
    protected final AtomicLong version;

    /**
     * Creates a new SingleSignOnEntry
     *
     * @param principal the <code>Principal</code> returned by the latest
     *                  call to <code>Realm.authenticate</code>.
     * @param authType  the type of authenticator used (BASIC, CLIENT_CERT,
     *                  DIGEST or FORM)
     * @param username  the username (if any) used for the authentication
     */
    public PayaraSingleSignOnEntry(String id, long ver, Principal principal, String authType, String username, String realmName) {
        super(principal, authType, username, null);
        this.id = id;
        this.version = new AtomicLong(ver);
        this.realmName = realmName;
        this.lastAccessTime = System.currentTimeMillis();
    }


    public String getRealmName() {
        return realmName;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long incrementAndGetVersion() {
        return version.incrementAndGet();
    }

    public long getVersion() {
        return version.get();
    }

    public boolean isEmpty() {
        return findSessions().isEmpty();
    }

}
