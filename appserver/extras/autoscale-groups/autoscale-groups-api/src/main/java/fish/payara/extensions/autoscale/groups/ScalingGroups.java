/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
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
package fish.payara.extensions.autoscale.groups;

import com.sun.enterprise.config.serverbeans.DomainExtension;
import fish.payara.internal.notification.PayaraNotifierConfiguration;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.DuckTyped;
import org.jvnet.hk2.config.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * "Group" ConfigBean interface which slots into Payara config at the domain level, holding references to each
 * individual {@link ScalingGroup}.
 *
 * @author Andrew Pielage
 */
@Configured
public interface ScalingGroups extends DomainExtension {

    /**
     * The list of all registered {@link ScalingGroup} ConfigBeans.
     *
     * @return The list of all registered {@link ScalingGroup} ConfigBeans.
     */
    @Element("*")
    List<ScalingGroup> getScalingGroups();

    /**
     * Return the {@link ScalingGroup} with the specified name
     * @param name The name of the {@link ScalingGroup} to return
     * @return The {@link ScalingGroup} with the matching name, or null if no match found
     */
    @DuckTyped
    ScalingGroup getScalingGroup(String name);

    @DuckTyped
    <T extends ScalingGroup> List<T> getScalingGroupsOfType(Class<T> type);

    class Duck {
        public static ScalingGroup getScalingGroup(ScalingGroups scalingGroups, String name) {
            for (ScalingGroup scalingGroup : scalingGroups.getScalingGroups()) {
                if (scalingGroup.getName().equals(name)) {
                    return scalingGroup;
                }
            }

            return null;
        }

        public static <T extends ScalingGroup> List<T> getScalingGroupsOfType(ScalingGroups scalingGroups, Class<T> type) {
            List<T> scalingGroupsOfTypeT = new ArrayList<>();
            for (ScalingGroup scalingGroup : scalingGroups.getScalingGroups()) {
                try {
                    scalingGroupsOfTypeT.add(type.cast(scalingGroup));
                } catch (Exception e) {
                    // Wrong type - Do nothing
                }
            }
            return scalingGroupsOfTypeT;
        }
    }
}
