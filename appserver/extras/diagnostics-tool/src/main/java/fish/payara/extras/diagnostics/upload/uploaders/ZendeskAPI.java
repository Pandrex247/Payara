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

package fish.payara.extras.diagnostics.upload.uploaders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fish.payara.extras.diagnostics.upload.Uploader;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.glassfish.api.logging.LogLevel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ZendeskAPI implements Uploader {
    Logger logger = Logger.getLogger(this.getClass().getName());

    private static final String ZENDESK_UPLOAD_URL = "https://payara.zendesk.com/api/v2/uploads";
    private static final int ZENDESK_UPLOAD_SUCCESS = 201;

    private static final String ZENDESK_ATTACH_URL = "https://payara.zendesk.com/api/v2/requests";
    private static final int ZENDESK_ATTACH_SUCCESS = 200;

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String COMMENT = "Attachment uploaded using the diagnostics tool.";
    private static final String NON_SUCCESS_MESSAGE = "was not successful. Upload will not continue.";

    private File file;
    private String username;
    private String password;
    private String ticketNumber;
    private OkHttpClient httpClient = new OkHttpClient();

    public ZendeskAPI(File file, String username, String password, String ticketNumber) {
        this.file = file;
        this.username = username;
        this.password = password;
        this.ticketNumber = ticketNumber;
    }

    
    /** 
     * Uploads and Attaches file to a ticket comment.
     * 
     * 0 - Success
     * 1 - Failure
     * 
     * @return int
     * @throws FileNotFoundException
     */
    @Override
    public int upload() throws FileNotFoundException {
        Map<String, String> headers = new HashMap<>();
        String authString = getAuthString(username, password);
        ObjectMapper objectMapper = new ObjectMapper();
        String token = "";
        
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String uploadUrl = ZENDESK_UPLOAD_URL + "?filename=" + file.getName();
        headers.put(AUTHORIZATION_HEADER, authString);
        headers.put(CONTENT_TYPE, "application/zip");
        RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("zip"), file))
                .build();


        Request uploadRequest = prepareRequest(uploadUrl, headers, "POST", requestBody);
        if(uploadRequest == null) {
            logger.log(LogLevel.SEVERE, "Upload request " + NON_SUCCESS_MESSAGE);
            return 1;
        }

        logger.log(LogLevel.INFO, "Starting upload to {0}", uploadUrl);
        
        Response uploadResponse = processRequest(uploadRequest, ZENDESK_UPLOAD_SUCCESS);
        if(uploadResponse == null) {
            logger.log(LogLevel.SEVERE, "Upload response " + NON_SUCCESS_MESSAGE);
            return 1;
        }

        try {
            assert uploadResponse.body() != null;
            token = objectMapper.readValue(uploadResponse.body().toString(), ZendeskResponse.class).getToken();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        String attachUrl = ZENDESK_ATTACH_URL + "/" + ticketNumber;        
        headers.replace(CONTENT_TYPE, "application/json");

        String jsonPayloadString = "";
        try {
            ZendeskRequest jsonPayloadObject = new ZendeskRequest();
            jsonPayloadObject.setComment(COMMENT);
            jsonPayloadObject.addUpload(token);
            
            jsonPayloadString = objectMapper.writeValueAsString(jsonPayloadObject);
        } catch (JsonProcessingException e) {
            logger.log(LogLevel.SEVERE, "Could not prepare JSON Payload for attach request. Upload will not continue");
            e.printStackTrace();
            return 1;
        }

        Request attachRequest = prepareRequest(attachUrl, headers, "PUT", requestBody);
        if(attachRequest == null) {
            logger.log(LogLevel.SEVERE, "Attach request " + NON_SUCCESS_MESSAGE);
            return 1;
        }

        logger.log(LogLevel.INFO, "Attaching file to ticket: {0}", ticketNumber);

        Response attachResponse = processRequest(attachRequest, ZENDESK_ATTACH_SUCCESS);
        if(attachResponse == null) {
            logger.log(LogLevel.SEVERE, "Attach response " + NON_SUCCESS_MESSAGE);
            return 1;
        }

        return 0;
    }

    
    /** 
     * Returns a BASIC authentication string from username and password.
     * 
     * @param username
     * @param password
     * @return String
     */
    private String getAuthString(String username, String password) {
        String authString = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
    }

    /** 
     * Prepares a HTTPRequest using specified attributes. Handles exceptions.
     * 
     * Only supports POST and PUT.
     * 
     * Returns null if request could not be built.
     * 
     * @param url
     * @param headers
     * @param httpMethod
     * @param publisher
     * @return HttpRequest
     */
    private Request prepareRequest(String url, Map<String, String> headers, String httpMethod, RequestBody publisher) {
        if(url == null || headers == null || httpMethod == null || publisher == null) {
            return null;
        }

        httpMethod = httpMethod.trim().toUpperCase();

        Request.Builder request = new Request.Builder();

        for(String key : headers.keySet()) {
            String value = headers.get(key);
            if(value != null) {
                request.addHeader(key, value);
            }
        }

        request.url(url);

        switch(httpMethod){
            case "PUT":
                request.put(publisher);
                break;
            case "POST":
                request.post(publisher);
                break;
            default:
                logger.log(LogLevel.SEVERE, "HTTP Method: {0} is not configured to be used with this service.", httpMethod);
                return null;
        }

        return request.build();
    }

    
    /** 
     * Sends the HTTP Request and handles the response. Returns null if successCode is not the one expected.
     * 
     * @param request
     * @param successCode
     * @return HttpResponse<String>
     */
    private Response processRequest(Request request, int successCode) {
        try (Response response = httpClient.newCall(request).execute()) {
            int httpStatus = response.code();
            logger.log(LogLevel.INFO, "Server Responded with {0}", httpStatus);
            if(httpStatus != successCode) {
                logger.log(LogLevel.SEVERE, "Upload did not succeed with HTTP code: {0}", successCode);
                return null;
            }
            return response;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static class ZendeskResponse {
        public Upload upload;

        public String getToken() {
            if(upload != null) {
                return upload.token;
            }
            return "";
        }

        public static class Upload {
            public String token;
        }
    }

    private static class ZendeskRequest {
        public Request request;

        public ZendeskRequest() {
            this.request = new Request();
        }

        public void addUpload(String value) {
            if(request != null) {
                request.comment.uploads.add(value);
            }
        }

        public void setComment(String value) {
            if(request != null) {
                request.comment.body = value;
            }
        }

        public static class Request {
            public Comment comment;

            public Request() {
                this.comment = new Comment();
            }

            public static class Comment {
                public String body;
                public List<String> uploads;

                public Comment() {
                    this.uploads = new ArrayList<>();
                }
            }
        }

    }
    
}
