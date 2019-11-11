package fish.payara.admin.servermgmt.cli;

import com.sun.enterprise.admin.servermgmt.cli.ChangeMasterPasswordCommandDAS;
import com.sun.enterprise.admin.servermgmt.cli.LocalDomainCommand;
import com.sun.enterprise.admin.servermgmt.pe.PEDomainsManager;
import com.sun.enterprise.universal.i18n.LocalStringsImpl;
import com.sun.enterprise.util.HostAndPort;
import com.sun.enterprise.util.net.NetUtils;
import org.glassfish.api.admin.CommandException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.security.common.FileProtectionUtility;
import org.jvnet.hk2.annotations.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;

@Service(name = "generate-encryption-key")
@PerLookup
public class GenerateEncryptionKey extends LocalDomainCommand {

    private static final String DATAGRID_KEY_FILE = "datagrid-key";
    private static final LocalStringsImpl SERVERMGMT_CLI_STRINGS =
            new LocalStringsImpl(ChangeMasterPasswordCommandDAS.class);

    @Override
    protected int executeCommand() throws CommandException {
        checkDomainIsNotRunning();
        verifyMasterPassword();

        File datagridKeyFile = new File(getDomainRootDir(), DATAGRID_KEY_FILE);
        if (!datagridKeyFile.exists()) {
            createDatagridEncryptionKeyFile(datagridKeyFile);
        }

        byte[] keyBytes = new byte[256/8];  // Key length is in bits !
        new SecureRandom().nextBytes(keyBytes);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        try {
            Files.write(datagridKeyFile.toPath(), key.getEncoded());
        } catch (IOException ioe) {
            throw new CommandException("Error writing encoded key to file", ioe);
        }

        return 0;
    }

    private void checkDomainIsNotRunning() throws CommandException {
        HostAndPort adminAddress = getAdminAddress();
        if (NetUtils.isRunning(adminAddress.getHost(), adminAddress.getPort())) {
            throw new CommandException(SERVERMGMT_CLI_STRINGS.get("domain.is.running",
                    getDomainName(), getDomainRootDir()));
        }
    }

    private void verifyMasterPassword() throws CommandException {
        PEDomainsManager manager = new PEDomainsManager();
        String masterpassword = super.readFromMasterPasswordFile();
        if (masterpassword == null) {
            masterpassword = passwords.get("AS_ADMIN_MASTERPASSWORD");
            if (masterpassword == null) {
                char[] masterpasswordChars = super.readPassword(SERVERMGMT_CLI_STRINGS.get("current.mp"));
                masterpassword = masterpasswordChars != null ? new String(masterpasswordChars) : null;
            }
        }
        if (masterpassword == null) {
            throw new CommandException(SERVERMGMT_CLI_STRINGS.get("no.console"));
        }
        if (!super.verifyMasterPassword(masterpassword)) {
            throw new CommandException(SERVERMGMT_CLI_STRINGS.get("incorrect.mp"));
        }
    }

    private void createDatagridEncryptionKeyFile(File datagridKeyFile) throws CommandException {
        try {
            // Windows defaults to essentially "7" for current user, Admins, and System
            FileProtectionUtility.chmod0600(datagridKeyFile);
            Files.createFile(datagridKeyFile.toPath());
        } catch (IOException ioe) {
            throw new CommandException(ioe.getMessage(), ioe);
        }

    }
}
