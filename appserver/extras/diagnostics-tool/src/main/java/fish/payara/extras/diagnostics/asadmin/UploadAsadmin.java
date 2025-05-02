///*
// * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
// *
// * Copyright (c) 2023-2024 Payara Foundation and/or its affiliates. All rights reserved.
// *
// * The contents of this file are subject to the terms of either the GNU
// * General Public License Version 2 only ("GPL") or the Common Development
// * and Distribution License("CDDL") (collectively, the "License").  You
// * may not use this file except in compliance with the License.  You can
// * obtain a copy of the License at
// * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
// * or packager/legal/LICENSE.txt.  See the License for the specific
// * language governing permissions and limitations under the License.
// *
// * When distributing the software, include this License Header Notice in each
// * file and include the License file at packager/legal/LICENSE.txt.
// *
// * GPL Classpath Exception:
// * Oracle designates this particular file as subject to the "Classpath"
// * exception as provided by Oracle in the GPL Version 2 section of the License
// * file that accompanied this code.
// *
// * Modifications:
// * If applicable, add the following below the License Header, with the fields
// * enclosed by brackets [] replaced by your own identifying information:
// * "Portions Copyright [year] [name of copyright owner]"
// *
// * Contributor(s):
// * If you wish your version of this file to be governed by only the CDDL or
// * only the GPL Version 2, indicate your decision by adding "[Contributor]
// * elects to include this software in this distribution under the [CDDL or GPL
// * Version 2] license."  If you don't indicate a single choice of license, a
// * recipient has the option to distribute your version of this file under
// * either the CDDL, the GPL Version 2 or to extend the choice of license to
// * its licensees as provided above.  However, if you add GPL Version 2 code
// * and therefore, elected the GPL Version 2 license, then the option applies
// * only if the new code is made subject to such option by the copyright
// * holder.
// */
//
//package fish.payara.extras.diagnostics.asadmin;
//
//import java.io.FileNotFoundException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.logging.Logger;
//
//import org.glassfish.api.Param;
//import org.glassfish.api.admin.CommandException;
//import org.glassfish.api.logging.LogLevel;
//import org.glassfish.hk2.api.PerLookup;
//import org.jvnet.hk2.annotations.Service;
//
//import fish.payara.extras.diagnostics.upload.UploadService;
//import fish.payara.extras.diagnostics.util.ParamConstants;
//import fish.payara.extras.diagnostics.util.PropertiesFile;
//
//@Service(name = "upload-diagnostics")
//@PerLookup
//public class UploadAsadmin extends BaseAsadmin {
//    Logger logger = Logger.getLogger(this.getClass().getName());
//
//    private static final String USERNAME_PARAM = ParamConstants.USERNAME_PARAM;
//    private static final String PASSWORD_PARAM = ParamConstants.PASSWORD_PARAM;
//    private static final String UPLOAD_DESTINATION_PARAM = ParamConstants.UPLOAD_DESTINATION_PARAM;
//    private static final String TICKET_NUM_PARAM = ParamConstants.TICKET_NUM_PARAM;
//
//    private static final String[] PARAMETER_OPTIONS = {USERNAME_PARAM, PASSWORD_PARAM, UPLOAD_DESTINATION_PARAM, DIR_PARAM, TICKET_NUM_PARAM};
//
//    @Param(name = USERNAME_PARAM, shortName = "u", optional = false)
//    private String username;
//
//    @Param(name = PASSWORD_PARAM, shortName="p", optional = false, password=true)
//    private String password;
//
//    @Param(name = UPLOAD_DESTINATION_PARAM, shortName="d", optional = false, acceptableValues = ParamConstants.NEXUS + ", " + ParamConstants.ZENDESK)
//    private String destination;
//
//    @Param(name = DIR_PARAM, shortName = "f", optional = true)
//    protected String dir;
//
//    @Param(name = TICKET_NUM_PARAM, shortName = "t", optional = true)
//    protected String ticketNum;
//
//    private UploadService uploadService;
//
//
//    /**
//     * Executes Asadmin command Upload.
//     *
//     * @return int
//     * @throws CommandException
//     */
//    @Override
//    protected int executeCommand() throws CommandException {
//        parameterMap = populateParameters(new HashMap<>(), PARAMETER_OPTIONS);
//        resolveDir(parameterMap);
//
//        uploadService = new UploadService(parameterMap);
//
//        try {
//            return uploadService.executeUpload();
//        } catch(FileNotFoundException fnfe) {
//            fnfe.printStackTrace();
//        }
//
//        return 1;
//    }
//
//
//    /**
//     * Returns a directory from either passed in parameter, or from properties file.
//     *
//     * @param params
//     * @return Map<String, String>
//     */
//    @Override
//    protected Map<String, String> resolveDir(Map<String, String> params) {
//        if(params == null) {
//            return params;
//        }
//
//        PropertiesFile props = getProperties();
//        if(props != null) {
//            Path path = props.getPath();
//            if(Files.exists(path)) {
//                String propValue = props.get(DIR_PARAM) + ".zip";
//                if(propValue != null) {
//                    params.put(DIR_PARAM, propValue);
//                    dir = propValue;
//                    logger.log(LogLevel.INFO, "Directory found from properties {0} ", dir);
//                }
//            }
//        }
//
//        logger.log(LogLevel.INFO, "Directory selected {0} ", dir);
//
//        return params;
//    }
//}
