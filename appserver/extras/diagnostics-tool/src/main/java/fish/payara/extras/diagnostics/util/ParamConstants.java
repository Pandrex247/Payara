/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2023-2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package fish.payara.extras.diagnostics.util;

public final class ParamConstants {
    //Base Params
    public static final String DIR_PARAM = "dir";
    public static final String PROPERTIES_PARAM = "properties";

    //Collect Params
    public static final String DOMAIN_NAME_PARAM = "domainName";
    public static final String TARGET_PARAM = "target";
    public static final String SERVER_LOG_PARAM = "serverLog";
    public static final String ACCESS_LOG_PARAM = "accessLog";
    public static final String NOTIFICATION_LOG_PARAM = "notificationLog";
    public static final String DOMAIN_XML_PARAM = "domainXml";
    public static final String OBFUSCATE_PARAM = "obfuscate";
    public static final String THREAD_DUMP_PARAM = "threadDump";
    public static final String JVM_REPORT_PARAM = "jvmReport";
    public static final String HEAP_DUMP_PARAM = "heapDump";
    public static final String NODE_DIR_PARAM = "nodeDir";
    //Upload Params
    public static final String USERNAME_PARAM = "username";
    public static final String PASSWORD_PARAM = "password";
    public static final String UPLOAD_DESTINATION_PARAM = "destination";
    public static final String TICKET_NUM_PARAM = "ticket";

    //Option Params
    public static final String DOMAIN_NAME = "DomainName";
    public static final String DOMAIN_XML_FILE_PATH = "DomainXMLFilePath";
    public static final String LOGS_PATH = "LogPath";

    public static final String NEXUS = "nexus";
    public static final String ZENDESK = "zendesk";

    private ParamConstants() {
    }

    ;
}
