package fish.payara.extras.diagnostics.asadmin;


import com.sun.enterprise.admin.cli.cluster.LocalInstanceCommand;
import com.sun.enterprise.admin.servermgmt.services.Strings;
import com.sun.enterprise.util.StringUtils;
import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandException;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import java.io.File;
import java.util.logging.Logger;

@Service(name = "_get-instance-install-dir")
@PerLookup
public class GetInstanceInstallDirCommand extends LocalInstanceCommand {
    public static final Logger LOGGER = Logger.getLogger(GetInstanceInstallDirCommand.class.getName());

    @Param(name = "instance_name", primary = true, optional = true)
    private String instanceName0;

    @Override
    protected void initInstance() throws CommandException {
        try {
            super.initInstance();
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandException(Strings.get("Instance does not exist on this machine"));
        }
    }

    @Override
    protected boolean mkdirs(File f) {
        return false;
    }

    @Override
    protected void validate() throws CommandException {
        instanceName = instanceName0;
        super.validate();
        if (!StringUtils.ok(getServerDirs().getServerName())) {
            throw new CommandException("Instance Name not specified");
        }

        File dasProperties = getServerDirs().getDasPropertiesFile();

        if (dasProperties.isFile()) {
            setDasDefaults(dasProperties);
        }

        if (!getServerDirs().getServerDir().isDirectory())
            throw new CommandException("Instance directory does not exist");
    }

    @Override
    protected int executeCommand() {
        LOGGER.info(getServerDirs().getServerDir().toString());
        return SUCCESS;
    }
}
