/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020-2023 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.extras.upgrade;

import com.sun.enterprise.admin.cli.CLICommand;
import com.sun.enterprise.util.OS;
import com.sun.enterprise.util.StringUtils;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Rolls back an upgrade
 *
 * @author Jonathan Coustick
 */
@Service(name = "rollback-server")
@PerLookup
public class RollbackUpgradeCommand extends BaseUpgradeCommand {

    @Override
    protected void validate() throws CommandException {
        // Perform usual validation; we don't want to skip it or alter it in anyway, we just want to add to it.
        super.validate();

        if (OS.isWindows()) {
            throw new CommandValidationException(
                    "Command not supported on Windows. Please use the rollbackUpgrade script.");
        }
    }

    @Override
    protected int executeCommand() {
        if (!Paths.get(glassfishDir, "modules.old").toFile().exists()) {
            logger.log(Level.SEVERE, "No old version found to rollback");
            return ERROR;
        }

        logger.log(Level.INFO, "Rolling back server...");

        // First up, remove any "staged" install
        try {
            deleteStagedInstall();
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Error cleaning up previous staged upgrade, aborting rollback: {0}",
                    ioe.toString());
            return ERROR;
        }

        // Second step, move "current" into "staged"
        try {
            logger.log(Level.FINE, "Moving current install into a staged rollback directory");
            for (String file : moveFolders) {
                try {
                    Path currentDirectory = Paths.get(glassfishDir, file);
                    Path newDirectory = Paths.get(glassfishDir, file + ".new");
                    logger.log(Level.FINER, "Moving {0} into staged rollback directory {1}",
                            new Object[]{currentDirectory.toString(), newDirectory.toString()});
                    Files.move(currentDirectory, newDirectory,
                            StandardCopyOption.REPLACE_EXISTING);
                    logger.log(Level.FINEST, "Moved {0} into staged rollback directory {1}",
                            new Object[]{currentDirectory.toString(), newDirectory.toString()});
                } catch (NoSuchFileException nsfe) {
                    if (nsfe.getMessage().contains("glassfish" + File.separator + ".." + File.separator + "mq")
                            && isWebDistributionUpgrade) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for mq directory under assumption " +
                                "this is a payara-web distribution. Continuing to move files...");
                        continue;
                    }

                    // osgi-cache directory is created when the domain is started, if it was never started the
                    // directory will not exist so it's safe to ignore the NSFE
                    if (nsfe.getMessage().contains("osgi-cache")) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for osgi-cache directory under the " +
                                "assumption the upgraded domain was never started. Continuing to move files...");
                        continue;
                    }

                    if (nsfe.getMessage().contains("glassfish/h2db")) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for glassfish/h2db directory under the " +
                                "assumption this is a distribution without the duplicate directory. Continuing to move files...");
                        continue;
                    }

                    throw nsfe;
                }
            }
            logger.log(Level.FINE, "Moved current install into a staged rollback directory");
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Error rolling back current install: {0}", ioe.toString());

            // Attempt to undo the rollback
            logger.log(Level.INFO, "Attempting to undo rollback");
            try {
                moveStagedToCurrent();
            } catch (IOException ioe1) {
                logger.log(Level.SEVERE, "Error undoing rollback: {0}", ioe1.toString());
            }

            return ERROR;
        }

        // Third step, move "old" into "current"
        try {
            logger.log(Level.FINE, "Moving old install back into current install");
            for (String file : moveFolders) {
                try {
                    Path oldDirectory = Paths.get(glassfishDir, file + ".old");
                    Path currentDirectory = Paths.get(glassfishDir, file);
                    logger.log(Level.FINER, "Moving old directory {0} into current install directory {1}",
                            new Object[]{oldDirectory.toString(), currentDirectory.toString()});
                    Files.move(oldDirectory, currentDirectory, StandardCopyOption.REPLACE_EXISTING);
                    logger.log(Level.FINEST, "Moved old directory {0} into current install directory {1}",
                            new Object[]{oldDirectory.toString(), currentDirectory.toString()});
                } catch (NoSuchFileException nsfe) {
                    if (nsfe.getMessage().contains("glassfish" + File.separator + ".." + File.separator + "mq")
                            && isWebDistributionUpgrade) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for mq directory under assumption " +
                                "this is a payara-web distribution. Continuing to move files...");
                        continue;
                    }

                    // osgi-cache directory is created when the domain is started, if it was never started before the
                    // upgrade the directory will not exist so it's safe to ignore the NSFE
                    if (nsfe.getMessage().contains("osgi-cache")) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for osgi-cache directory under the " +
                            "assumption the upgraded domain was never started. Continuing to move files...");
                        continue;
                    }

                    if (nsfe.getMessage().contains("glassfish/h2db")) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for glassfish/h2db directory under the " +
                                "assumption this is a distribution without the duplicate directory. Continuing to move files...");
                        continue;
                    }

                    throw nsfe;
                }
            }
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Error rolling back current install: {0}", ioe.toString());

            // Attempt to undo the rollback
            logger.log(Level.INFO, "Attempting to undo rollback");

            // First up, move "current" back to "old"
            try {
                moveCurrentToOld();
            } catch (IOException ioe1) {
                logger.log(Level.SEVERE, "Error undoing rollback: {0}", ioe1.toString());
                return ERROR;
            }

            // After moving "current" back to "old", move "staged" back to "current"
            try {
                moveStagedToCurrent();
            } catch (IOException ioe1) {
                logger.log(Level.SEVERE, "Error undoing rollback: {0}", ioe1.toString());
            }

            return ERROR;
        }

        // Fourth step, roll back the nodes for all domains
        try {
            logger.log(Level.INFO, "Rolling back nodes");
            reinstallNodes();
            logger.log(Level.INFO, "Rolled back nodes");
        } catch (IOException | ConfigurationException ex) {
            // IOException or ConfigurationException occurs when parsing the domain.xml, before any attempt to
            // update the nodes. It gets thrown if the domain.xml couldn't be found, or if the domain.xml is
            // somehow incorrect, which implies something has gone wrong - rollback
            logger.log(Level.SEVERE, "Error rolling back nodes: {0}", ex.toString());

            // Attempt to undo the rollback
            logger.log(Level.INFO, "Attempting to undo rollback");

            // First up, move "current" back to "old"
            try {
                moveCurrentToOld();
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Error undoing rollback: {0}", ioe.toString());
                return ERROR;
            }

            // After moving "current" back to "old", move "staged" back to "current"
            try {
                moveStagedToCurrent();
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Error undoing rollback: {0}", ioe.toString());
                return ERROR;
            }

            // Exception gets thrown before any command is run, so we don't need to reinstall the nodes
            return ERROR;
        } catch (CommandException ce) {
            // CommandException gets thrown once all nodes have been attempted to be rolled back and if at
            // least one roll back hit an error. We don't want to undo the roll back since the failure might be valid
            logger.log(Level.WARNING, "Failed to roll back all nodes: inspect the logs from this command for " +
                            "the reasons. You can roll back the node installs individually using the " +
                            "rollback-server command on each node, or attempt to roll them all back again using the " +
                            "reinstall-nodes command. \n{0}",
                    ce.getMessage());
            return WARNING;


        }

        // Fifth step, remove "staged" again - if we've reached this point the install should have been successfully
        // rolled back so we don't need it anymore
        boolean logWarning = false;
        try {
            deleteStagedInstall();
        } catch (IOException ioe) {
            // Log the error, but we don't need to fail the command and exit out at this point
            logger.log(Level.WARNING, "Error cleaning up rolled back upgrade: {0}", ioe.toString());
            logWarning = true;
        }

        // Final step, restore the original domain configs
        // The osgi-caches must be stored in a temp directory while the domain is restored so they are not overwritten
        try {
            Map<String, Path> tempOsgiCacheDirs = storeOsgiCache();
            restoreDomains();
            restoreOsgiCache(tempOsgiCacheDirs);
        } catch (CommandException | IOException ce) {
            logger.log(Level.WARNING, "Error restore-domain command! " +
                    "Please restore your domain config manually. \n{0}", ce.toString());
            logWarning = true;
        }

        if (logWarning) {
            return WARNING;
        }

        return SUCCESS;
    }

    private void moveStagedToCurrent() throws IOException {
        logger.log(Level.INFO, "Moving staged back to current");
        for (String file : moveFolders) {
            Path stagedPath = Paths.get(glassfishDir, file + ".new");
            Path targetPath = Paths.get(glassfishDir, file);

            // Only move the stuff that exists - if it doesn't, it implies it was never moved to begin with
            if (!stagedPath.toFile().exists()) {
                logger.log(Level.FINEST, "Staged file {0} does not exist, skipping", stagedPath.toString());
                continue;
            }

            // Use copy with overwrite since we don't know what state the move was in
            CopyFileVisitor copyFileVisitor = new CopyFileVisitor(stagedPath, targetPath);
            Files.walkFileTree(stagedPath, copyFileVisitor);
        }

        // Now delete
        deleteStagedInstall();

        logger.log(Level.INFO, "Moved staged back to current");
    }

    private void moveCurrentToOld() throws IOException {
        logger.log(Level.INFO, "Moving current install back to old");
        for (String file : moveFolders) {
            Path currentPath = Paths.get(glassfishDir, file);
            Path targetPath = Paths.get(glassfishDir, file + ".old");

            // Only move the stuff that exists - if it doesn't, it implies it was never moved to begin with
            if (!currentPath.toFile().exists()) {
                logger.log(Level.FINEST, "Current install file {0} does not exist, skipping", currentPath.toString());
                continue;
            }

            // Use copy with overwrite since we don't know what state the move was in
            CopyFileVisitor copyFileVisitor = new CopyFileVisitor(currentPath, targetPath);
            Files.walkFileTree(currentPath, copyFileVisitor);
        }

        // Now delete
        deleteCurrentInstall();

        logger.log(Level.INFO, "Moved current install back to old");
    }

    private void deleteCurrentInstall() throws IOException {
        DeleteFileVisitor visitor = new DeleteFileVisitor();
        for (String folder : moveFolders) {
            // Only attempt to delete folders which exist
            // Don't fail out if it doesn't exist, just keep going - we want to delete all we can
            Path folderPath = Paths.get(glassfishDir, folder);
            if (folderPath.toFile().exists()) {
                Files.walkFileTree(folderPath, visitor);
            } else {
                logger.log(Level.FINEST, "Current install file {0} does not exist, skipping", folderPath.toString());
            }
        }
    }

    private void restoreDomains() throws CommandException {
        logger.log(Level.INFO, "Restoring domain configs");
        File[] domaindirs = getDomainsDir().listFiles(File::isDirectory);
        for (File domaindir : domaindirs) {
            CLICommand restoreDomainCommand = CLICommand.getCommand(habitat, "restore-domain");
            if (StringUtils.ok(domainDirParam)) {
                logger.log(Level.FINE, "Executing command: {0}", "restore-domain --domaindir "
                        + domainDirParam + " " + domaindir.getName());
                restoreDomainCommand.execute("restore-domain", "--domaindir", domainDirParam, domaindir.getName());
            } else {
                logger.log(Level.FINE, "Executing command: {0}", "restore-domain " + domaindir.getName());
                restoreDomainCommand.execute("restore-domain", domaindir.getName());
            }

        }
    }

    /**
     * Used to store the osgi-cache directories for each domain which has the directory so they are not lost
     * when the restore-domain command is run.
     *
     * @return A map of domain names to the corresponding temp osgi-cache directory paths
     * @throws IOException
     */
    private Map<String, Path> storeOsgiCache() throws IOException {
        Map<String, Path> osgiCacheDirs = new HashMap<>();
        File[] domaindirs = getDomainsDir().listFiles(File::isDirectory);
        for (File domaindir : domaindirs) {
            if (new File(domaindir + File.separator + "osgi-cache").exists()) {
                Path tempDirectory = Files.createTempDirectory(domaindir.getName()+"-osgi-cache");
                Path targetPath = Paths.get(domaindir + File.separator + "osgi-cache");
                logger.log(Level.FINER, "Backing up osgi-cache {0} to temp directory {1}",
                        new Object[]{targetPath.toString(), tempDirectory.toString()});
                Files.move(targetPath, tempDirectory, StandardCopyOption.REPLACE_EXISTING);
                logger.log(Level.FINEST, "Backed up osgi-cache {0} to temp directory {1}",
                        new Object[]{targetPath.toString(), tempDirectory.toString()});
                osgiCacheDirs.put(domaindir.getName(), tempDirectory);
            } else {
                logger.log(Level.FINEST, "No osgi-cache found for domain dir {0}, skipping", domaindir.toString());
            }
        }
        return osgiCacheDirs;
    }

    /**
     * Used to restore the osgi-cache directories back into their corresponding domains.
     *
     * @param osgiCacheDirs A map of domain names to the corresponding temp osgi-cache directory paths
     * @throws IOException
     */
    private void restoreOsgiCache(Map<String, Path> osgiCacheDirs) throws IOException {
        for (Map.Entry<String, Path> tempCacheDir : osgiCacheDirs.entrySet()) {
            Path targetPath = Paths.get(glassfishDir + File.separator + "domains" + File.separator +
                    tempCacheDir.getKey() + File.separator + "osgi-cache");
            logger.log(Level.FINER, "Restoring osgi-cache {0} to current install directory {1}",
                    new Object[]{tempCacheDir.getValue().toString(), targetPath.toString()});
            Files.move(tempCacheDir.getValue(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.log(Level.FINEST, "Restored osgi-cache {0} to current install directory {1}",
                    new Object[]{tempCacheDir.getValue().toString(), targetPath.toString()});
        }
    }

}
