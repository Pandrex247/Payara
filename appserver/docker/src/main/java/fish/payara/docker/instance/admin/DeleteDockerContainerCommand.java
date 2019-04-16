package fish.payara.docker.instance.admin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Service(name = "_delete-docker-container")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@RestEndpoints({
        @RestEndpoint(configBean = Domain.class,
                opType = RestEndpoint.OpType.DELETE,
                path = "_delete-docker-container",
                description = "Deletes a Docker Container")
})
public class DeleteDockerContainerCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(DeleteDockerContainerCommand.class.getName());

    @Param(name = "instanceName", alias = "instance", primary = true)
    private String instanceName;

    @Param(name = "nodeName", alias = "node")
    private String nodeName;

    @Inject
    Nodes nodes;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport actionReport = adminCommandContext.getActionReport();

        // Shouldn't need to check for presence of this nodeName, as its presence was checked for in DeleteInstanceCommand
        Node node = nodes.getNode(nodeName);

        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target("http://"
                + node.getNodeHost()
                + ":"
                + node.getDockerPort()
                + "/containers/"
                + instanceName);

        Response response = webTarget.request(MediaType.APPLICATION_JSON).delete();

        // Check status of response and act on result
        Response.StatusType responseStatus = response.getStatusInfo();
        if (responseStatus.getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
            // woohoo
        } else {
            actionReport.failure(logger, "Failed to delete Docker Container: \n" + responseStatus.getReasonPhrase());
        }
    }
}
