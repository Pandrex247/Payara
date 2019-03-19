package fish.payara.cluster.docker;


import com.sun.enterprise.util.StringUtils;
import com.sun.enterprise.v3.admin.cluster.NodeUtils;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ParameterMap;

import javax.inject.Inject;

public class CreateNodeDockerCommand implements AdminCommand {

    @Param(name = "name", primary = true)
    String name;

    @Param(name = NodeUtils.PARAM_NODEHOST)
    String nodehost;

    @Param(name = NodeUtils.PARAM_NODEDIR, optional= true)
    String nodedir;

    @Param(name = NodeUtils.PARAM_INSTALLDIR, optional= true)
    String installdir;

    @Param(name = NodeUtils.PARAM_DOCKER_IMAGE)
    String dockerImage;

    @Param(name = NodeUtils.PARAM_DOCKER_PORT, optional = true)
    Integer dockerPort;

    @Param(name = NodeUtils.PARAM_TLS_CERT, optional = true)
    String tlsCert;

    @Param(name = NodeUtils.PARAM_TLS_CA, optional = true)
    String tlsCa;

    @Param(name = NodeUtils.PARAM_TLS_PEM, optional = true)
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
            map.add(NodeUtils.PARAM_NODEDIR, nodedir);
        }

        if (StringUtils.ok(installdir)) {
            map.add(NodeUtils.PARAM_INSTALLDIR, installdir);
        }

        if (StringUtils.ok(dockerImage)) {
            map.add(NodeUtils.PARAM_DOCKER_IMAGE, dockerImage);
        }

        if (dockerPort != null) {
            map.add(NodeUtils.PARAM_DOCKER_PORT, Integer.toString(dockerPort));
        }

        if (StringUtils.ok(tlsCert)) {
            map.add(NodeUtils.PARAM_TLS_CERT, tlsCert);
        }

        if (StringUtils.ok(tlsCa)) {
            map.add(NodeUtils.PARAM_TLS_CA, tlsCa);
        }

        if (StringUtils.ok(tlsPem)) {
            map.add(NodeUtils.PARAM_TLS_PEM, tlsPem);
        }

        map.add(NodeUtils.PARAM_TYPE,"DOCKER");
        commandInvocation.parameters(map);
        commandInvocation.execute();

        NodeUtils.sanitizeReport(actionReport);
    }
}
