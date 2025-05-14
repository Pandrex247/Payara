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

package fish.payara.extras.diagnostics.upload;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

import org.glassfish.api.logging.LogLevel;

import fish.payara.extras.diagnostics.upload.uploaders.NexusAPI;
import fish.payara.extras.diagnostics.upload.uploaders.ZendeskAPI;
import fish.payara.extras.diagnostics.util.ParamConstants;

public class UploadService {
    Logger logger = Logger.getLogger(this.getClass().getName());

    private Map<String, String> params;

    public UploadService(Map<String, String> params) {
        this.params = params;
    }

    
    /** 
     * Builds and calls upload() for the selected service.
     * 
     * 0 - Success
     * 1 - Failure
     * 
     * @return int
     * @throws FileNotFoundException
     */
    public int executeUpload() throws FileNotFoundException {
        String serviceName = params.get(ParamConstants.UPLOAD_DESTINATION_PARAM);

        if(serviceName == null) {
            logger.log(LogLevel.SEVERE, "A valid service could not be found");
            return 0;
        }

        ServiceAPIBuilder apiBuilder = new ServiceAPIBuilder(serviceName, params);
        Uploader uploader = apiBuilder.getAPI();

        if(uploader != null) {
            return uploader.upload();
        }

        return 1;
    }

    /**
     * Factory class for intialising a service.
     */
    private static class ServiceAPIBuilder {
        Logger logger = Logger.getLogger(this.getClass().getName());

        private String APIName;
        private Map<String, String> params;

        public ServiceAPIBuilder(String APIName, Map<String, String> params) {
            this.APIName = APIName.trim().toLowerCase();
            this.params = params;
        }

        /**
         * Returns the API service based on the APIName.
         * 
         * @return Uploader
         */
        public Uploader getAPI() {
            if (APIName == null || params == null) {
                return null;
            }

            String username = params.get(ParamConstants.USERNAME_PARAM);
            String password = params.get(ParamConstants.PASSWORD_PARAM);
            File file = null;

            boolean valid = false;
            valid = validateParam(ParamConstants.USERNAME_PARAM, params, "Username was not valid or missing. Upload will not continue.");
            valid = validateParam(ParamConstants.PASSWORD_PARAM, params, "Password was not valid or missing. Upload will not continue.");

            if(!valid) {
                return null;
            } 

            String pathString = params.get(ParamConstants.DIR_PARAM);
            if (validateParam(ParamConstants.DIR_PARAM, params)) {
                Path path = Paths.get(pathString);
                if (Files.exists(path)) {
                    file = new File(pathString);
                }
            }

            if(file == null) {
                if(pathString != null) {
                    logger.log(LogLevel.WARNING, "File {0} could not be found, upload will not continue.", pathString);
                } else {
                    logger.log(LogLevel.WARNING, "File could not be found, upload will not continue.");
                }
                return null;
            }

            switch (APIName) {
                case ParamConstants.NEXUS:
                    return new NexusAPI(file, username, password);
                case ParamConstants.ZENDESK:
                    String ticketNum = params.get(ParamConstants.TICKET_NUM_PARAM);
                    boolean validTicket = validateParam(ParamConstants.TICKET_NUM_PARAM, params, "Ticket number was not correct or non existing.");

                    if(!validTicket) {
                        break;
                    }

                    return new ZendeskAPI(file, username, password, ticketNum);
                default:
                    logger.log(LogLevel.INFO, "Could not find or create service with name: {0}", APIName);
            }

            return null;
        }
        
        /**
         * Returns boolean value if paremters provided are valid.
         * 
         * @param key
         * @param params
         * @param message
         * @return boolean
         */
        private boolean validateParam(String key, Map<String, String> params, String message) {
            if(params == null || key == null) {
                return false;
            } 

            String value = params.get(key);
            if(value == null) {
                if(message != null) {
                    logger.log(LogLevel.WARNING, message);
                } else {
                    logger.log(LogLevel.WARNING, "Parameter {0} was null or was incorrect.", key);
                }
                return false;
            }

            return true;
        }

        /**
         * Overload method which prints a default message.
         * 
         * @param key
         * @param params
         * @return boolean
         */
        private boolean validateParam(String key, Map<String, String> params) {
            return validateParam(key, params, null);
        }

    }
}
