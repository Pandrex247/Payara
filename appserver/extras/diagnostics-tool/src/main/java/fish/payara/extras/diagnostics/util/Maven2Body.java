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

import java.nio.file.Path;

public class Maven2Body {
    private Path asset;
    private String extension;
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private boolean generatePom;

    public final static String MAVEN2_ASSET = "maven2.asset1";
    public final static String MAVEN2_EXTENSION = "maven2.asset1.extension";
    public final static String MAVEN2_GROUPID = "maven2.groupId";
    public final static String MAVEN2_ARTIFACTID = "maven2.artifactId";
    public final static String MAVEN2_VERSION = "maven2.version";
    public final static String MAVEN2_PACKAGING = "maven2.packaging";
    public final static String MAVEN2_GENERATE_POM = "maven2.generate-pom";

    public Path getAsset() {
        return asset;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public void setAsset(Path asset) {
        this.asset = asset;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public boolean getGeneratePom() {
        return generatePom;
    }

    public String getGeneratePomString() {
        return String.valueOf(generatePom);
    }

    public void setGeneratePom(boolean generatePom) {
        this.generatePom = generatePom;
    }

    public Maven2BodyBuilder newBuilder() {
        return new Maven2BodyBuilder();
    }

    public class Maven2BodyBuilder {
        private Maven2Body maven2Body;

        public Maven2BodyBuilder() {
            this.maven2Body = new Maven2Body();
        }

        public Maven2BodyBuilder asset(Path value) {
            this.maven2Body.setAsset(value);
            return this;
        }

        public Maven2BodyBuilder extension(String value) {
            this.maven2Body.setExtension(value);
            return this;
        }

        public Maven2BodyBuilder groupId(String value) {
            this.maven2Body.setGroupId(value);
            return this;
        }

        public Maven2BodyBuilder artifactId(String value) {
            this.maven2Body.setArtifactId(value);
            return this;
        }

        public Maven2BodyBuilder version(String value) {
            this.maven2Body.setVersion(value);
            return this;
        }

        public Maven2BodyBuilder packaging(String value) {
            this.maven2Body.setPackaging(value);
            return this;
        }

        public Maven2BodyBuilder generatePom(Boolean value) {
            this.maven2Body.setGeneratePom(value);
            return this;
        }

        public Maven2Body build() {
            return this.maven2Body;
        }

    }

}
