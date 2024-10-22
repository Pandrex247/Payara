package fish.payara.upgrade.javamail;

import com.sun.enterprise.config.serverbeans.Resources;
import jakarta.inject.Inject;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.resources.javamail.config.MailResource;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.Transaction;

@Service
@RunLevel(StartupRunLevel.VAL)
public class UpgradeJavaMail implements PostConstruct {
    private static final String OLD_PACKAGE = "com.sun.mail";
    private static final String NEW_PACKAGE = "org.eclipse.angus.mail";

    @Inject
    Resources resources;

    @Override
    public void postConstruct () {
        for (MailResource resource : this.resources.getResources(MailResource.class)) {
            if (resource == null) {
                continue;
            }

            if (resource.getStoreProtocolClass().startsWith(OLD_PACKAGE)) {
                Transaction transaction = new Transaction();
                try {
                    MailResource writableResource = transaction.enroll(resource);
                    writableResource.setStoreProtocolClass(
                        resource.getStoreProtocolClass().replace(OLD_PACKAGE, NEW_PACKAGE)
                    );
                    transaction.commit();
                }
                catch (Exception e) {
                    transaction.rollback();
                }
            }

            if (resource.getTransportProtocolClass().startsWith(OLD_PACKAGE)) {
                Transaction transaction = new Transaction();
                try {
                    MailResource writableResource = transaction.enroll(resource);
                    writableResource.setTransportProtocolClass(
                        writableResource.getTransportProtocolClass().replace(OLD_PACKAGE, NEW_PACKAGE)
                    );
                    transaction.commit();
                }
                catch (Exception e) {
                    transaction.rollback();
                }
            }
        }
    }
}
