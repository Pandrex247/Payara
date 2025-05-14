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

import fish.payara.extras.diagnostics.upload.Uploader;
import fish.payara.extras.diagnostics.util.Maven2Body;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.glassfish.api.logging.LogLevel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

public class NexusAPI implements Uploader {
    Logger logger = Logger.getLogger(this.getClass().getName());

    private static final String NEXUS_URL = "https://nexus.payara.fish/service/rest/v1/components";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_MULTIPART_BOUNDARY = "multipart/form-data; boundary=";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String REPO_SYS_PROP_NAME = "fish.payara.diagnostics.repo";

    private static final int SUCCESS_HTTP_RESPONSE_CODE = 204;

    private File file;
    private String username;
    private String password;

    /**
     * Will upload specified file to Payara Nexus, using specified username and password for authentication.
     * The repository that it will be uploaded to depends on the value of the {@value REPO_SYS_PROP_NAME} property
     *
     * @param file
     * @param username
     * @param password
     */
    public NexusAPI(File file, String username, String password) {
        this.file = file;
        this.username = username;
        this.password = password;
    }

    /**
     * Constructs a Maven2 body as requested by Nexus, then uploads to the provided nexus URL using multipart/form-data.
     *
     * @return int
     */
    @Override
    public int upload() {

        Maven2Body maven2Body = new Maven2Body().newBuilder()
                .asset(file.toPath())
                .extension("zip")
                .groupId("fish.payara.extras")
                .artifactId(file.getName().substring(0, file.getName().lastIndexOf(".")))
                .version(new Timestamp(System.currentTimeMillis()).toString())
                .packaging("zip")
                .generatePom(true)
                .build();

        RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(Maven2Body.MAVEN2_ASSET, maven2Body.getAsset().toString())
                .addFormDataPart(Maven2Body.MAVEN2_EXTENSION, maven2Body.getExtension())
                .addFormDataPart(Maven2Body.MAVEN2_GROUPID, maven2Body.getGroupId())
                .addFormDataPart(Maven2Body.MAVEN2_ARTIFACTID, maven2Body.getArtifactId())
                .addFormDataPart(Maven2Body.MAVEN2_VERSION, maven2Body.getVersion())
                .addFormDataPart(Maven2Body.MAVEN2_PACKAGING, maven2Body.getPackaging())
                .addFormDataPart(Maven2Body.MAVEN2_GENERATE_POM, maven2Body.getGeneratePomString())
                .build();

        String url = resolveNexusURL();
        Request request = new Request.Builder().url(url)
                .addHeader(CONTENT_TYPE, CONTENT_TYPE_MULTIPART_BOUNDARY + UUID.randomUUID().toString())
                .addHeader(AUTHORIZATION_HEADER, getAuthString(username, password))
                .post(requestBody)
                .build();

        OkHttpClient httpClient1 = new OkHttpClient();

        logger.log(LogLevel.INFO, "Starting upload to {0}", url);
        try (Response response = httpClient1.newCall(request).execute()) {
            int responseCode = response.code();
            logger.log(LogLevel.INFO, "Server Responded with {0}", responseCode);

            if (responseCode != SUCCESS_HTTP_RESPONSE_CODE) {
                return 1;
            }

        } catch (IOException e) {
            logger.log(LogLevel.SEVERE, "IOException occured trying to send to {0}", NEXUS_URL);
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Returns BASIC UTF-8 encoded string from username and password arguments.
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
     * Returns the URI required to upload to a repository.
     * <p>
     * Will use username as the repository target, or {@value REPO_SYS_PROP_NAME} system property if not null.
     *
     * @return URI
     */
    private String resolveNexusURL() {
        StringBuilder builder = new StringBuilder(NEXUS_URL).append("?repository=");
        String repo = System.getProperty(REPO_SYS_PROP_NAME);
        if (repo != null) {
            builder.append(repo);
        } else if (username != null) {
            builder.append(username);
        }
        return builder.toString();
    }

}
