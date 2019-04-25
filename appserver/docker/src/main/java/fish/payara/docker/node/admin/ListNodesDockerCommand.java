package fish.payara.docker.node.admin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.util.ColumnFormatter;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;

@Service(name = "list-nodes-docker")
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@RestEndpoints({
        @RestEndpoint(configBean= Domain.class,
                opType=RestEndpoint.OpType.GET,
                path="list-nodes-docker",
                description="Lists information about all Docker nodes registered to this domain")
})
public class ListNodesDockerCommand implements AdminCommand {

    @Inject
    private Nodes nodes;

    @Param(name = "verbose", defaultValue = "false", optional = true)
    private boolean verbose;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        String OUTPUT_HEADERS[];
        if (!verbose) {
            OUTPUT_HEADERS = new String[]{"Name", "Host", "Image", "Port"};
        } else {
            OUTPUT_HEADERS = new String[]{"Name", "Host", "Image", "Port", "TLS Cert", "TLS CA", "TLS PEM"};
        }

        ColumnFormatter columnFormatter = new ColumnFormatter(OUTPUT_HEADERS);

        for (Node node : nodes.getNode()) {
            if (node.getType().equals("DOCKER")) {
                Object[] nodeInfo;
                if (!verbose) {
                    nodeInfo = new Object[]{
                            node.getName(),
                            node.getNodeHost(),
                            node.getDockerImage(),
                            node.getDockerPort()
                    };
                } else {
                    nodeInfo = new Object[]{
                            node.getName(),
                            node.getNodeHost(),
                            node.getDockerImage(),
                            node.getDockerPort(),
                            node.getTlsCert(),
                            node.getTlsCa(),
                            node.getTlsPem()
                    };
                }
                columnFormatter.addRow(nodeInfo);
            }
        }

        adminCommandContext.getActionReport().setMessage(columnFormatter.toString());
    }
}
