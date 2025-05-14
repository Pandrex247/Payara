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

package fish.payara.extensions.autoscale.groups.core.admin;

import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.enterprise.config.serverbeans.DeploymentGroups;
import fish.payara.extensions.autoscale.groups.ScalingGroup;
import fish.payara.extensions.autoscale.groups.admin.ScalingGroupCommand;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RestParam;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import jakarta.inject.Inject;
import java.util.Properties;

@Service(name = "get-deployment-group-scaling-group")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
@RestEndpoints({
        @RestEndpoint(configBean = DeploymentGroup.class,
                opType = RestEndpoint.OpType.GET,
                path = "get-deployment-group-scaling-group",
                description = "Gets the Scaling Group configured for this Deployment Group",
                params = {
                        @RestParam(name = "name", value = "$parent")
                }
        )
})
public class GetDeploymentGroupScalingGroupCommand extends ScalingGroupCommand {

    @Inject
    private DeploymentGroups deploymentGroups;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        try {
            validateParams();
        } catch (CommandValidationException commandValidationException) {
            adminCommandContext.getActionReport().setFailureCause(commandValidationException);
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        ScalingGroup scalingGroup = null;

        // Search through the scaling groups, checking for the one with a reference to our requested deployment group
        for (ScalingGroup scalingGroupIterator : scalingGroups.getScalingGroups()) {
            if (scalingGroupIterator.getDeploymentGroupRef().equals(name)) {
                scalingGroup = scalingGroupIterator;
                break;
            }
        }

        if (scalingGroup == null) {
            // Don't mark as a failure - it is acceptable for a deployment group to not have a scaling group
            adminCommandContext.getActionReport().setMessage("Deployment Group " + name +
                    " does not have a Scaling Group");
            return;
        }

        adminCommandContext.getActionReport().setMessage("Scaling Group: " + scalingGroup.getName());

        Properties extraProps = new Properties();
        extraProps.put("scalingGroupName", scalingGroup.getName());
        adminCommandContext.getActionReport().setExtraProperties(extraProps);
    }

    @Override
    protected void validateParams() throws CommandValidationException {
        super.validateParams();

        if (deploymentGroups.getDeploymentGroup(name) == null) {
            throw new CommandValidationException("Deployment Group " + name + "does not exist");
        }
    }
}
