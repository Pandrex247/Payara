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

package fish.payara.extensions.autoscale.groups.core.admin;

import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.enterprise.config.serverbeans.DeploymentGroups;
import fish.payara.extensions.autoscale.groups.Scaler;
import fish.payara.extensions.autoscale.groups.ScalingGroup;
import fish.payara.extensions.autoscale.groups.ScalingGroups;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RestParam;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import java.util.List;

/**
 * Asadmin Command for scaling up the number of instances within a {@link DeploymentGroup Deployment Group} using its
 * configured {@link ScalingGroup Scaling Group}.
 *
 * Instead of looking up the target {@link DeploymentGroup Deployment Group} directly and then using that to grab the
 * {@link ScalingGroup Scaling Group} config, we search through the {@link ScalingGroups Scaling Groups} for a
 * {@link ScalingGroup Scaling Group} that is linked to the target {@link DeploymentGroup Deployment Group}. This is
 * done since the {@link DeploymentGroup Deployment Group} doesn't hold a reference to the
 * {@link ScalingGroup Scaling Group} so as to allow the Core Server to not depend on the AutoScale Groups plugin.
 *
 * @author Andrew Pielage
 */
@Service(name = "scale-up")
@PerLookup
@CommandLock(CommandLock.LockType.NONE) // Defer locking to any chained commands
@ExecuteOn(RuntimeType.DAS)
@RestEndpoints({
        @RestEndpoint(configBean = DeploymentGroup.class,
                opType = RestEndpoint.OpType.POST,
                path = "scale-up",
                description = "Scales up a Deployment Group",
                params = {
                        @RestParam(name = "id", value = "$parent")
                }
        )
})
public class ScaleUpCommand extends ScaleCommand {

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        try {
            validateParams();
        } catch (CommandValidationException commandValidationException) {
            adminCommandContext.getActionReport().setFailureCause(commandValidationException);
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        for (ScalingGroup scalingGroup : scalingGroups.getScalingGroups()) {
            if (scalingGroup.getDeploymentGroupRef().equals(target)) {
                // Get the Scaler implementation service for this scaling group type
                List<Scaler> scalerServices = serviceLocator.getAllServices(Scaler.class);
                for (Scaler scalerService : scalerServices) {
                    // Since we're working with a ConfigBeanProxy we can't simply do getClass() since this would return
                    // the proxy class. Instead, we can grab the interfaces of this proxy to what's actually being
                    // proxied. In this case, each ConfigBeanProxy *should* only only have a single interface: the
                    // scaling group config bean interface that we're trying to compare (e.g. NodesScalingGroup)
                    if (scalerService.getScalingGroupClass().equals(scalingGroup.getClass().getInterfaces()[0])) {
                        adminCommandContext.setActionReport(scalerService.scaleUp(quantity, scalingGroup));

                        if (adminCommandContext.getActionReport().hasFailures()) {
                            if (adminCommandContext.getActionReport().hasSuccesses()) {
                                adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.WARNING);
                            } else {
                                adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
                            }
                        } else if (adminCommandContext.getActionReport().hasWarnings()) {
                            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.WARNING);
                        }
                        return;
                    }
                }
                break;
            }
        }

        // If we've got to here, there was no match
        adminCommandContext.getActionReport().setMessage(
                "Deployment group does not appear to have a configured scaling group.");
        adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
    }
}
