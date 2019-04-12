package fish.payara.docker.instance.admin;

import com.sun.enterprise.config.serverbeans.Server;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RestParam;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

@Service(name = "_start-docker-container")
@CommandLock(CommandLock.LockType.NONE)
@PerLookup
@RestEndpoints({
        @RestEndpoint(configBean= Server.class,
                opType=RestEndpoint.OpType.POST,
                path="_start-docker-container",
                description="Starts the Docker contain that this instance exists on",
                params={
                        @RestParam(name="id", value="$parent")
                })
})
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
