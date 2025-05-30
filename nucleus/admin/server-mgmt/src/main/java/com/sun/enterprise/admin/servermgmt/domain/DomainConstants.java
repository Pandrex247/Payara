/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
// Portions Copyright 2023-2025 Payara Foundation and/or its affiliates

package com.sun.enterprise.admin.servermgmt.domain;

public class DomainConstants {

    /** Filename contains encrypted admin credentials. */
    public static final String ADMIN_KEY_FILE = "admin-keyfile";

    /** Name of configuration directory. */
    public static final String CONFIG_DIR = "config";

    /** Name of binary directory. */
    public static final String BIN_DIR = "bin";

    /** The file name stores the basic domain information. */
    public static final String DOMAIN_INFO_XML = "domain-info.xml";

    /** Domain password file name. */
    public static final String DOMAIN_PASSWORD_FILE = "domain-passwords";

    /** Name of directory stores the domain information. */
    public static final String INFO_DIRECTORY = "init-info";

    /**
     * Filename contains the server certificates, including its private key.
     */
    public static final String KEYSTORE_FILE = "keystore.p12";

    /**
     * Master password file name stores the password for secure key store.
     */
    public static final String MASTERPASSWORD_FILE = "master-password";

    /**
     * File name for the file that contains the path to the master password.
     */
    public static final String MASTERPASSWORD_LOCATION_FILE = "master-password-location";

    /**
     * Filename contains the trusted certificates, including public keys.
     */
    public static final String TRUSTSTORE_FILE = "cacerts.p12";

    /**
     * Fallback TRUSTSTORE_FILE if the P12 format is not found, use JKS format
     */
    public static final String TRUSTSTORE_JKS_FILE = "cacerts.jks";

    /**
     * Filename contains most of the domain configuration.
     */
    public static final String DOMAIN_XML_FILE = "domain.xml";
}
