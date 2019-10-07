package fish.payara.docker.node.admin;

import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;

import static org.glassfish.api.admin.RestEndpoint.OpType.POST;

@Service(name = "delete-temp-nodes")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
@RestEndpoints({
        @RestEndpoint(configBean = Nodes.class,
                opType = POST,
                path = "delete-temp-nodes",
                description = "Deletes all temporary nodes not in use")
})
public class DeleteTempNodesCommand implements AdminCommand {

    @Inject
    private Nodes nodes;

    @Inject
    private CommandRunner commandRunner;

    @Override
    public void execute(AdminCommandContext context) {
        for (Node node : nodes.getNode()) {
            if (node.getType().equals("TEMP") && !node.nodeInUse() && commandRunner != null) {
                ParameterMap parameterMap = new ParameterMap();
                parameterMap.add("name", node.getName());
                commandRunner.getCommandInvocation("_delete-node-temp",
                        context.getActionReport(),
                        context.getSubject())
                        .parameters(parameterMap)
                        .execute();
            }
        }
    }
}
