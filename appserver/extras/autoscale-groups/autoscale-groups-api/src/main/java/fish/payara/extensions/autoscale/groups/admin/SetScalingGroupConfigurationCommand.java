/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021-2024 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.extensions.autoscale.groups.admin;

import com.sun.enterprise.config.serverbeans.Configs;
import com.sun.enterprise.util.StringUtils;
import fish.payara.enterprise.config.serverbeans.DeploymentGroups;
import fish.payara.extensions.autoscale.groups.ScalingGroup;
import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandValidationException;

import jakarta.inject.Inject;

/**
 * Parent class intended to be extended from for any Scaling Groups "set" commands, containing common validation and
 * parameters.
 *
 * @author Andrew Pielage
 */
public abstract class SetScalingGroupConfigurationCommand extends ScalingGroupCommand {

    @Param(name = "deploymentGroup", alias = "deploymentgroup", optional = true)
    protected String deploymentGroupRef;

    @Param(name = "config", optional = true)
    protected String configRef;

    @Inject
    protected DeploymentGroups deploymentGroups;

    @Inject
    protected Configs configs;

    @Override
    protected void validateParams() throws CommandValidationException {
        super.validateParams();

        // Check the scaling group actually exists
        if (scalingGroups.getScalingGroup(name) == null) {
            throw new CommandValidationException("Scaling group with name " + name + " does not exist");
        }

        // Check that the deployment group is valid and exists
        if (StringUtils.ok(deploymentGroupRef)) {
            if (deploymentGroups.getDeploymentGroup(deploymentGroupRef) == null) {
                throw new CommandValidationException("Deployment Group " + deploymentGroupRef + " does not exist");
            }

            // Search through the scaling groups, checking for any duplicates of the deployment group ref
            for (ScalingGroup scalingGroup : scalingGroups.getScalingGroups()) {
                if (!scalingGroup.getName().equals(name) && scalingGroup.getDeploymentGroupRef().equals(deploymentGroupRef)) {
                    throw new CommandValidationException("Deployment Group " + deploymentGroupRef + " is already in " +
                            "use by " + scalingGroup.getName());
                }
            }
        }

        // Check that the config is valid and exists
        if (StringUtils.ok(configRef)) {
            if (configs.getConfigByName(configRef) == null) {
                throw new CommandValidationException("Config name " + configRef + " is not valid or doesn't exist");
            }
        }
    }

}
