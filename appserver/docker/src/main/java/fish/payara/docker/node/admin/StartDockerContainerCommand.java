package fish.payara.docker.node.admin;

import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;


public class StartDockerContainerCommand implements AdminCommand {

    @Param
    String node;

    @Param
    String instanceName;

    /**
     * Executes the command with the command parameters passed as Properties
     * where the keys are the parameter names and the values are the parameter values
     *
     * @param context information
     */
    @Override
    public void execute(AdminCommandContext context) {

    }
}
