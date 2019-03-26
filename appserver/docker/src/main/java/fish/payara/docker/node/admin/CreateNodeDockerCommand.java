package fish.payara.docker.node.admin;


import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.util.StringUtils;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;

@Service(name = "create-node-docker")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@CommandLock(CommandLock.LockType.NONE)
@TargetType(value = {CommandTarget.DAS})
@RestEndpoints({
        @RestEndpoint(configBean= Domain.class,
                opType=RestEndpoint.OpType.POST,
                path="create-node-docker",
                description="Create a Docker Node to spawn containers on")
})
public class CreateNodeDockerCommand implements AdminCommand {

    @Param(name = "name", primary = true)
    String name;

    @Param(name = "nodehost")
    String nodehost;

    @Param(name = "nodedir", optional= true)
    String nodedir;

    @Param(name = "installdir", optional= true)
    String installdir;

    @Param(name = "dockerImage")
    String dockerImage;

    @Param(name = "dockerPort", optional = true)
    Integer dockerPort;

    @Param(name = "tlsCert", optional = true)
    String tlsCert;

    @Param(name = "tlsCa", optional = true)
    String tlsCa;

    @Param(name = "tlsPem", optional = true)
    String tlsPem;

    @Inject
    private CommandRunner commandRunner;

    /**
     * Executes the command with the command parameters passed as Properties
     * where the keys are the parameter names and the values are the parameter values
     *
     * @param adminCommandContext information
     */
    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport actionReport = adminCommandContext.getActionReport();

        CommandRunner.CommandInvocation commandInvocation = commandRunner.getCommandInvocation(
                "_create-node", actionReport, adminCommandContext.getSubject());

        ParameterMap map = new ParameterMap();
        map.add("DEFAULT", name);

        if (StringUtils.ok(nodedir)) {
            map.add("nodedir", nodedir);
        }

        if (StringUtils.ok(installdir)) {
            map.add("installdir", installdir);
        }

        if (StringUtils.ok(dockerImage)) {
            map.add("dockerImage", dockerImage);
        }

        if (dockerPort != null) {
            map.add("dockerPort", Integer.toString(dockerPort));
        }

        if (StringUtils.ok(tlsCert)) {
            map.add("tlsCert", tlsCert);
        }

        if (StringUtils.ok(tlsCa)) {
            map.add("tlsCa", tlsCa);
        }

        if (StringUtils.ok(tlsPem)) {
            map.add("tlsPem", tlsPem);
        }

        map.add("type","DOCKER");
        commandInvocation.parameters(map);
        commandInvocation.execute();
    }
}
