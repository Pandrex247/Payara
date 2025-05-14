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

import com.sun.appserv.server.util.Version;
import com.sun.enterprise.admin.cli.CLICommand;
import com.sun.enterprise.universal.i18n.LocalStringsImpl;
import com.sun.enterprise.util.JDK;
import com.sun.enterprise.util.OS;
import com.sun.enterprise.util.StringUtils;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.glassfish.api.ExecutionContext;
import org.glassfish.api.Param;
import org.glassfish.api.ParamDefaultCalculator;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.CommandModel;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigurationException;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Command to upgrade Payara server to a newer version
 *
 * @author Jonathan Coustick
 */
@Service(name = "upgrade-server")
@PerLookup
public class UpgradeServerCommand extends BaseUpgradeCommand {

    private static final String USE_DOWNLOADED_PARAM_NAME = "useDownloaded";
    private static final String USERNAME_PARAM_NAME = "username";
    private static final String NEXUS_PASSWORD_PARAM_NAME = "nexusPassword";
    private static final String VERSION_PARAM_NAME = "version";

    @Param(name = USERNAME_PARAM_NAME, optional = true)
    private String username;

    @Param(name = NEXUS_PASSWORD_PARAM_NAME, password = true, optional = true, alias = "nexuspassword")
    private String nexusPassword;

    @Param(defaultValue = "payara", acceptableValues = "payara, payara-ml, payara-web, payara-web-ml", optional = true)
    private String distribution;

    @Param(name = VERSION_PARAM_NAME, optional = true)
    private String version;

    @Param(name = "stage", optional = true, defaultCalculator = DefaultStageParamCalculator.class)
    private boolean stage;

    @Param(name = USE_DOWNLOADED_PARAM_NAME, optional = true, alias = "usedownloaded")
    private File useDownloadedFile;

    private static final String NEXUS_URL = System.getProperty("fish.payara.upgrade.repo.url",
            "https://nexus.payara.fish/repository/payara-enterprise-downloadable-artifacts/fish/payara/distributions/");
    private static final String ZIP = ".zip";
    private static final LocalStringsImpl strings = new LocalStringsImpl(CLICommand.class);

    private static final String PERMISSIONS = "rwxr-xr-x";

    private boolean isPayara6Upgrade = false;

    @Override
    protected void prevalidate() throws CommandException {
        // Perform usual pre-validation; we don't want to skip it or alter it in anyway, we just want to add to it
        super.prevalidate();

        // If useDownloaded is present, check it's present. If it isn't, we need to pre-validate the download parameters
        // again with optional set to false so as to mimic a "conditional optional".
        // Note that we can't use the parameter variables here since CLICommand#inject() hasn't been called yet
        if (getOption(USE_DOWNLOADED_PARAM_NAME) != null) {
            if (!Paths.get(getOption(USE_DOWNLOADED_PARAM_NAME)).toFile().exists()) {
                throw new CommandValidationException("File specified does not exist: " + useDownloadedFile);
            }
        } else {
            if (getOption(USERNAME_PARAM_NAME) == null) {
                prevalidateParameter(USERNAME_PARAM_NAME);
            }

            if (getOption(VERSION_PARAM_NAME) == null) {
                prevalidateParameter(VERSION_PARAM_NAME);
            }

            if (getOption(NEXUS_PASSWORD_PARAM_NAME) == null) {
                prevalidatePasswordParameter(NEXUS_PASSWORD_PARAM_NAME);
            }
        }
    }

    /**
     * Adapted from method in parent class, namely {@link CLICommand#prevalidate()}
     *
     * @param parameterName
     * @throws CommandValidationException
     */
    private void prevalidateParameter(String parameterName) throws CommandValidationException {
        // if option isn't set, prompt for it (if interactive), otherwise throw an error
        if (programOpts.isInteractive()) {
            try {
                // Build the terminal if it isn't present
                buildTerminal();
                buildLineReader();

                // Prompt for it
                if (getOption(parameterName) == null && lineReader != null) {
                    String val = lineReader.readLine(strings.get("optionPrompt", parameterName.toLowerCase(Locale.ENGLISH)));
                    if (ok(val)) {
                        options.set(parameterName, val);
                    }
                }
                // if it's still not set, that's an error
                if (getOption(parameterName) == null) {
                    logger.log(Level.INFO, strings.get("missingOption", "--" + parameterName));
                    throw new CommandValidationException(strings.get("missingOptions", parameterName));
                }
            } finally {
                closeTerminal();
            }
        } else {
            throw new CommandValidationException(strings.get("missingOptions", parameterName));
        }
    }

    /**
     * A method to get the Payara Version from a downloaded file. When --usedownloaded is specified, the upgrade tool
     * is unaware of the version being upgraded to. This will read the `glassfish-version.properties` file to get the
     * version of Payara.
     *
     * @param extractedDownloadPath Path to the unzipped directory where the new version is stored.
     * @return A string version number eg. 6.0.0
     * @throws CommandValidationException If the glassfish-version.properties file cannot be found, read or is incorrect
     */
    private String getPayaraVersionFromDownload(Path extractedDownloadPath) throws CommandValidationException {
        //The directory should contain the payaraX folder. The exact name is unknown as it's the major version. Get the name of that folder
        String payaraDirectoryName;
        try {
            //Check the zip only contains one file, and it's named payaraX where X is a number eg. payara6
            File[] rootDirectoryFiles = extractedDownloadPath.toFile().listFiles();
            if (rootDirectoryFiles == null || rootDirectoryFiles.length != 1 || !rootDirectoryFiles[0].getName().matches("payara\\d+")) {
                throw new CommandValidationException("The Payara zip provided has been modified. Please use an unmodified distribution");
            }
            payaraDirectoryName = rootDirectoryFiles[0].getName();
        } catch (IndexOutOfBoundsException e) {
            throw new CommandValidationException(getOption(USE_DOWNLOADED_PARAM_NAME) + " is empty.");
        }

        //Get the path to the glassfish-version.properties file
        Path glassfishVersionFile = Paths.get(extractedDownloadPath + File.separator + payaraDirectoryName +
                File.separator + "glassfish" + File.separator + "config" + File.separator + "branding" + File.separator + "glassfish-version.properties");

        //Read the glassfish-version.properties file to extract the version components
        List<String> versionComponents;
        try (Stream<String> glassfishVersionPropertiesFileStream = Files.lines(glassfishVersionFile)) {
            //Go through each line of the file, find anything that matches the pattern, extract the numbers and add them to a list
            Pattern pattern = Pattern.compile(".*_version=([0-9]+)");
            versionComponents = glassfishVersionPropertiesFileStream
                    .map(line -> {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            return matcher.group(1);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (versionComponents.size() != 3) {
                throw new CommandValidationException("glassfish-version.properties file does not contain a valid Payara version.");
            }
            return versionComponents.get(0) + "." + versionComponents.get(1) + "." + versionComponents.get(2);
        } catch (IOException ioException) {
            throw new CommandValidationException("Unable to find glassfish-version.properties file.");
        } catch (IndexOutOfBoundsException | NullPointerException e) {
            throw new CommandValidationException("Unable to get Payara version from glassfish-version.properties file.");
        }
    }

    protected void throwCommandValidationException(String currentVersion, String selectedVersion)
            throws CommandValidationException {
        String message = String.format("The version indicated is incorrect. You can't downgrade " +
                        "from %s to %s please set correct version and try again",
                currentVersion, selectedVersion);
        throw new CommandValidationException(message);
    }

    /**
     * Adapted from method in parent class, namely CLICommand#initializeCommandPassword
     *
     * @param passwordParameterName
     * @throws CommandValidationException
     */
    private void prevalidatePasswordParameter(String passwordParameterName) throws CommandValidationException {
        // Get the ParamModel
        CommandModel.ParamModel passwordParam = commandModel.getParameters()
                .stream().filter(paramModel -> paramModel.getName().equalsIgnoreCase(passwordParameterName))
                .findFirst().orElse(null);

        // Get the password
        char[] passwordChars = null;
        if (passwordParam != null) {
            passwordChars = getPassword(passwordParam.getName(), passwordParam.getLocalizedPrompt(),
                    passwordParam.getLocalizedPromptAgain(), false);
        }

        if (passwordChars == null) {
            // if not terse, provide more advice about what to do
            String msg;
            if (programOpts.isTerse()) {
                msg = strings.get("missingPassword", name, passwordParameterName);
            } else {
                msg = strings.get("missingPasswordAdvice", name, passwordParameterName);
            }

            throw new CommandValidationException(msg);
        }

        options.set(passwordParameterName, new String(passwordChars));
    }

    @Override
    protected void validateDistribution(String versionDistribution) throws CommandValidationException {
        super.validateDistribution(versionDistribution);

        // Check if distribution is the same as the one specified.
        if (!versionDistribution.equalsIgnoreCase(distribution)) {
            throw new CommandValidationException(String.format("The current distribution (%s) you are " +
                    "running does not match the requested upgrade distribution (%s)", versionDistribution, distribution));
        }
    }

    @Override
    protected void validate() throws CommandException {
        // Perform usual validation; we don't want to skip it or alter it in anyway, we just want to add to it
        super.validate();

        // Check that someone hasn't manually specified --stage=false, on Windows it should default to true since
        // in-place upgrades aren't supported
        if (OS.isWindows() && !stage) {
            throw new CommandValidationException("Non-staged upgrades are not supported on Windows.");
        }

        if (getOption(USE_DOWNLOADED_PARAM_NAME) == null) {
            validateVersions();
        }

        // If upgrading to Payara 6, make sure we're using JDK 11 or higher
        if (isPayara6Upgrade) {
            validateJavaVersion();
        }

        // Create property files
        createPropertiesFile();
        createBatFile();
    }

    /**
     * Parses the current and future versions and validates them.
     *
     * @throws CommandValidationException
     */
    protected void validateVersions() throws CommandValidationException {
        List<String> versionList = getVersion();
        if (versionList.isEmpty()) {
            String message = "Empty selected version, please verify and try again";
            throw new CommandValidationException(message);
        }

        String selectedVersion = versionList.get(0).trim();
        Pattern pattern = Pattern.compile("([0-9]{1,2})\\.([0-9]{1,2})\\.([0-9]{1,2})(?!\\W\\w+)");
        Matcher matcher = pattern.matcher(selectedVersion);
        if (matcher.find()) {
            if (matcher.groupCount() != 3) {
                String message = String.format("Invalid selected version %s, please verify and try again",
                        selectedVersion);
                throw new CommandValidationException(message);
            }

            int upgradeMajorVersion = Integer.parseInt(matcher.group(1).trim());

            preventVersionDowngrade(selectedVersion,
                    upgradeMajorVersion, Integer.parseInt(getCurrentMajorVersion()),
                    Integer.parseInt(matcher.group(2).trim()), Integer.parseInt(getCurrentMinorVersion()),
                    Integer.parseInt(matcher.group(3).trim()), Integer.parseInt(getCurrentUpdatedVersion()));

            if (upgradeMajorVersion == 6) {
                isPayara6Upgrade = true;
            }
        } else {
            //To provide a helpful error, check if the version was likely a community version
            Pattern communityPattern = Pattern.compile("([0-9]{1,2})\\.([0-9]{4})\\.([0-9]{1,2})(?!\\W\\w+)");
            Matcher communityMatcher = communityPattern.matcher(selectedVersion);
            if (communityMatcher.find()) {
                String message = String.format("%s is a Payara Community version. You can only upgrade to a Payara Enterprise version",
                        selectedVersion);
                throw new CommandValidationException(message);
            }

            //If it wasn't a community version it's likely random text
            String message = String.format("Invalid selected version %s, please verify and try again",
                    selectedVersion);
            throw new CommandValidationException(message);
        }
    }

    /**
     * Method to prevent downgrade of current Payara version vs the option --version indicated for the
     * upgrade-server command
     *
     * @throws CommandValidationException
     */
    private void preventVersionDowngrade(String selectedVersion,
            int majorSelectedVersion, int majorCurrentVersion,
            int minorSelectedVersion, int minorCurrentVersion,
            int updateSelectedVersion, int updatedCurrentVersion) throws CommandValidationException {
        StringBuilder buildCurrentVersion = new StringBuilder().append(majorCurrentVersion).append(".")
                .append(minorCurrentVersion).append(".").append(updatedCurrentVersion);
        if (majorSelectedVersion < majorCurrentVersion) {
            throwCommandValidationException(buildCurrentVersion.toString(), selectedVersion);
        } else if (!(majorSelectedVersion > majorCurrentVersion)
                && (minorSelectedVersion < minorCurrentVersion)) {
            throwCommandValidationException(buildCurrentVersion.toString(), selectedVersion);
        } else if (!(majorSelectedVersion > majorCurrentVersion)
                && !(minorSelectedVersion > minorCurrentVersion)
                && (updateSelectedVersion < updatedCurrentVersion)) {
            throwCommandValidationException(buildCurrentVersion.toString(), selectedVersion);
        } else if (selectedVersion.equalsIgnoreCase(buildCurrentVersion.toString())) {
            String message = String
                    .format("It was selected the same version: selected version %s and current version %s" +
                                    ", please verify and try again",
                            selectedVersion, buildCurrentVersion);
            throw new CommandValidationException(message);
        }
    }

    /**
     * Method to get selected Payara version from Upgrade command
     *
     * @return List<String>
     */
    protected List<String> getVersion() {
        return options.get(VERSION_PARAM_NAME);
    }


    /**
     * Method to get the Current Payara Major Version
     *
     * @return String
     */
    protected String getCurrentMajorVersion() {
        return Version.getMajorVersion().trim();
    }

    /**
     * Method to get the Current Payara Minor Version
     *
     * @return String
     */
    protected String getCurrentMinorVersion() {
        return Version.getMinorVersion().trim();
    }

    /**
     * Method to get the Current Payara Updated Version
     *
     * @return String
     */
    protected String getCurrentUpdatedVersion() {
        return Version.getUpdateVersion().trim();
    }

    /**
     * Method to get the Payara version being upgraded to Major Version
     *
     * @return Major version of the Payara distribution being upgraded to as a String
     */
    protected String getUpgradeMajorVersion() {
        //This is a list but only ever stores one value. Split on the . and get the Major version
        return options.get(VERSION_PARAM_NAME).get(0).split("\\.")[0];
    }

    /**
     * Method to get the Payara version being upgraded to Minor Version
     *
     * @return Minor version of the Payara distribution being upgraded to as a String
     */
    protected String getUpgradeMinorVersion() {
        //This is a list but only ever stores one value. Split on the . and get the Minor version
        return options.get(VERSION_PARAM_NAME).get(0).split("\\.")[1];
    }

    /**
     * Method to get the Payara version being upgraded to Update Version
     *
     * @return Update version of the Payara distribution being upgraded to as a String
     */
    protected String getUpgradeUpdateVersion() {
        //This is a list but only ever stores one value. Split on the . and get the Update version
        return options.get(VERSION_PARAM_NAME).get(0).split("\\.")[2];
    }

    private void validateJavaVersion() throws CommandValidationException {
        int majorVersion = JDK.getMajor();
        if (majorVersion < 11) {
            throw new CommandValidationException("The minimum JDK requirement for Payara 6 is JDK 11, see documentation for more details.");
        }
    }

    /**
     * Creates the upgrade-tool.properties file expected to be used by the applyStagedUpgrade, cleanupUpgrade, and
     * rollbackUpgrade scripts. If a file is already present, it will be deleted.
     *
     * @throws CommandValidationException If there's an issue deleting or creating the properties file
     */
    private void createPropertiesFile() throws CommandValidationException {
        // Perform file separator substitution for Linux if required
        String[] folders = Arrays.copyOf(moveFolders, moveFolders.length);
        if (OS.isWindows()) {
            for (int i = 0; i < folders.length; i++) {
                folders[i] = folders[i].replace("\\", "/");
            }
        }

        // Delete existing property file if present
        Path upgradeToolPropertiesPath = Paths.get(glassfishDir, "config", "upgrade-tool.properties");
        logger.log(Level.FINER, "Creating upgrade-tool.properties file: {0}", upgradeToolPropertiesPath.toString());

        if (Files.exists(upgradeToolPropertiesPath)) {
            try {
                logger.log(Level.FINER, "Deleting pre-existing upgrade-tool.properties file: {0}",
                        upgradeToolPropertiesPath.toString());
                Files.delete(upgradeToolPropertiesPath);
                logger.log(Level.FINER, "Deleted pre-existing upgrade-tool.properties file: {0}",
                        upgradeToolPropertiesPath.toString());
            } catch (IOException ioException) {
                throw new CommandValidationException("Encountered an error trying to existing delete " +
                        "upgrade-tool.properties file:\n", ioException);
            }
        }

        // Create new property file and populate
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(upgradeToolPropertiesPath.toFile()))) {
            // Add variable name expected by scripts
            bufferedWriter.append(PAYARA_UPGRADE_DIRS_PROP + "=");

            // Add the move folders, separated by commas
            String joinedFolders = String.join(",", folders);
            logger.log(Level.FINEST, "Populating upgrade-tool.properties with move folders: {0}", joinedFolders);
            bufferedWriter.append(joinedFolders);
        } catch (IOException ioException) {
            throw new CommandValidationException("Encountered an error trying to write upgrade-tool.properties file:\n",
                    ioException);
        }
        logger.log(Level.FINER, "Finished creating upgrade-tool.properties file: {0}",
                upgradeToolPropertiesPath.toString());
    }

    /**
     * Creates the upgrade-tool.bat file expected to be used by the applyStagedUpgrade.bat, cleanupUpgrade.bat, and
     * rollbackUpgrade.bat scripts. If a file is already present, it will be deleted.
     *
     * @throws CommandValidationException If there's an issue deleting or creating the properties file
     */
    private void createBatFile() throws CommandValidationException {
        // Perform file separator substitution for Windows if required
        String[] folders = Arrays.copyOf(moveFolders, moveFolders.length);
        if (!OS.isWindows()) {
            for (int i = 0; i < folders.length; i++) {
                folders[i] = folders[i].replace("/", "\\");
            }
        }

        // Delete existing property file if present
        Path upgradeToolBatPath = Paths.get(glassfishDir, "config", "upgrade-tool.bat");
        logger.log(Level.FINER, "Creating upgrade-tool.bat file: {0}", upgradeToolBatPath.toString());
        if (Files.exists(upgradeToolBatPath)) {
            try {
                logger.log(Level.FINER, "Deleting pre-existing upgrade-tool.bat file: {0}",
                        upgradeToolBatPath.toString());
                Files.delete(upgradeToolBatPath);
                logger.log(Level.FINEST, "Deleted pre-existing upgrade-tool.bat file: {0}",
                        upgradeToolBatPath.toString());
            } catch (IOException ioException) {
                throw new CommandValidationException("Encountered an error trying to existing delete " +
                        "upgrade-tool.bat file:\n", ioException);
            }
        }

        // Create new property file and populate
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(upgradeToolBatPath.toFile()))) {
            // Add variable name expected by scripts
            bufferedWriter.append("SET " + PAYARA_UPGRADE_DIRS_PROP + "=");

            // Add the move folders, separated by commas
            String joinedFolders = String.join(",", folders);
            logger.log(Level.FINEST, "Populating upgrade-tool.bat with move folders: {0}", joinedFolders);
            bufferedWriter.append(joinedFolders);
        } catch (IOException ioException) {
            throw new CommandValidationException("Encountered an error trying to write upgrade-tool.bat file:\n",
                    ioException);
        }
        logger.log(Level.FINER, "Finished creating upgrade-tool.bat file: {0}", upgradeToolBatPath.toString());
    }

    @Override
    public int executeCommand() {
        String url = NEXUS_URL + distribution + "/" + version + "/" + distribution + "-" + version + ZIP;
        String basicAuthString = username + ":" + nexusPassword;
        String authBytes = "Basic " + Base64.getEncoder().encodeToString(basicAuthString.getBytes());

        Path unzippedDirectory = null;

        // here the upgrade starts with non-restorable changes, display warning
        logger.log(Level.WARNING, "Do not interrupt the upgrade process, do not shutdown the server or computer.");

        // Download and/or unzip payara distribution, aborting upgrade if this fails
        try {
            Path tempFile = Files.createTempFile("payara", ".zip");

            if (useDownloadedFile != null) {
                logger.log(Level.FINER, "Copying downloaded distribution {0} to temp file: {1}",
                        new Object[]{useDownloadedFile.toString(), tempFile.toString()});
                Files.copy(useDownloadedFile.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
                logger.log(Level.FINEST, "Copied downloaded distribution {0} to temp file: {1}",
                        new Object[]{useDownloadedFile.toString(), tempFile.toString()});
            } else {
                logger.log(Level.INFO, "Downloading new Payara version...");
                logger.log(Level.FINE, "Downloading from {0}", url);
                HttpURLConnection connection = getConnection(url);
                connection.setRequestProperty("Authorization", authBytes);

                int code = connection.getResponseCode();
                if (code != 200) {
                    if (code == 404) {
                        logger.log(Level.SEVERE, "The version indicated is incorrect, please set correct version and try again");
                        throw new CommandValidationException("Payara version not found");
                    } else {
                        logger.log(Level.SEVERE, "Error connecting to server: {0}", code);
                        return ERROR;
                    }
                }

                logger.log(Level.FINER, "Copying downloaded distribution to temp file: {0}", tempFile);
                Files.copy(connection.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
                logger.log(Level.FINEST, "Copied downloaded distribution to temp file: {0}", tempFile);
            }

            FileInputStream unzipFileStream = new FileInputStream(tempFile.toFile());
            logger.log(Level.FINE, "Extracting zip file {0}", tempFile.toString());
            unzippedDirectory = extractZipFile(unzipFileStream);
            logger.log(Level.FINEST, "Extracted zip file {0}", tempFile.toString());
        } catch (IOException | CommandException e) {
            logger.log(Level.SEVERE, String.format("Error preparing for upgrade, aborting upgrade: %s", e));
            return ERROR;
        }
        if (unzippedDirectory == null) {
            logger.log(Level.SEVERE, "Error preparing for upgrade, aborting upgrade: could not extract archive");
            return ERROR;
        }

        //If a downloaded file is used the upgrade version isn't known and can only be determined after the download was unzipped.
        if (useDownloadedFile != null) {
            try {
                //Get the version from the downloaded file, validate it then set the version option.
                options.add(VERSION_PARAM_NAME, getPayaraVersionFromDownload(unzippedDirectory));
            } catch (CommandException commandException) {
                logger.log(Level.SEVERE, "Error getting version from provided zip, aborting upgrade: {0}", commandException.toString());
                return ERROR;
            }

            try {
                validateVersions();

                if (isPayara6Upgrade) {
                    validateJavaVersion();
                }
            } catch (CommandException commandException) {
                logger.log(Level.SEVERE, commandException.toString());
                return ERROR;
            }

        }

        // Attempt to backup domains, exiting out if it fails
        try {
            backupDomains();
        } catch (CommandException ce) {
            logger.log(Level.SEVERE, "Error executing backup-domain command, aborting upgrade: {0}", ce.toString());
            return ERROR;
        }

        try {
            cleanupExisting();
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Error cleaning up previous upgrades, aborting upgrade: {0}", ioe.toString());
            return ERROR;
        }

        try {
            moveFiles(unzippedDirectory);

            if (!OS.isWindows()) {
                fixPermissions();
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error upgrading Payara Server, rolling back upgrade: {0}", ex.toString());

            try {
                if (stage) {
                    deleteStagedInstall();
                } else {
                    undoMoveFiles();
                }
            } catch (IOException ex1) {
                logger.log(Level.WARNING, "Failed to restore previous state: {0}", ex.toString());
            }
            return ERROR;
        }

        // Don't reinstall the nodes if we're staging, since we'll just be reinstalling them with the "current" version
        if (!stage) {
            try {
                reinstallNodes();
            } catch (IOException | ConfigurationException ex) {
                // IOException or ConfigurationException occurs when parsing the domain.xml, before any attempt to
                // update the nodes. It gets thrown if the domain.xml couldn't be found, or if the domain.xml is
                // somehow incorrect, which implies something has gone wrong - rollback
                logger.log(Level.SEVERE, "Error upgrading Payara Server nodes, rolling back: {0}", ex.toString());
                try {
                    if (stage) {
                        deleteStagedInstall();
                    } else {
                        undoMoveFiles();
                    }
                } catch (IOException ex1) {
                    // Exit out here if we failed to restore, we don't want to push a broken install to the nodes
                    logger.log(Level.SEVERE, "Failed to restore previous state of local install", ex1.toString());
                    return ERROR;
                }

                // Exception gets thrown before any command is run, so we don't need to reinstall the nodes
                return ERROR;
            } catch (CommandException ce) {
                // CommandException gets thrown once all nodes have been attempted to be upgraded and if at
                // least one upgrade hit an error. We don't want to roll back now since the failure might be valid
                logger.log(Level.WARNING, "Failed to upgrade all nodes: inspect the logs from this command for " +
                                "the reasons. You can rollback the server upgrade and all of its nodes using the " +
                                "rollback-server command, upgrade the node installs individually using the " +
                                "upgrade-server command on each node, or attempt to upgrade them all again using the " +
                                "reinstall-nodes command. \n{0}",
                        ce.getMessage());
                return WARNING;
            }
        }

        if (stage) {
            logger.log(Level.INFO,
                    "Upgrade successfully staged, please run the applyStagedUpgrade script to apply the upgrade. " +
                            "It can be found under payara" + getCurrentMajorVersion() + "/glassfish/bin.");
        }

        return SUCCESS;
    }

    /**
     * Method to return HttpURLConnection from String url
     *
     * @param url of type String
     * @return HttpURLConnection
     * @throws IOException
     */
    protected HttpURLConnection getConnection(String url) throws IOException {
        URL nexusUrl = new URL(url);
        return (HttpURLConnection) nexusUrl.openConnection();
    }

    private Path extractZipFile(InputStream remote) throws IOException {
        Path tempDirectory = Files.createTempDirectory("payara-new");

        logger.log(Level.FINER, "Extracting zip file to temp directory {0}", tempDirectory.toString());
        try (ZipInputStream zipInput = new ZipInputStream(remote)) {
            ZipEntry entry = zipInput.getNextEntry();
            while (entry != null) {
                Path endPath = tempDirectory.resolve(entry.getName());
                if (entry.isDirectory()) {
                    endPath.toFile().mkdirs();
                } else {
                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(endPath.toFile()))) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInput.read(buffer)) != -1) {
                            out.write(buffer, 0, length);
                        }
                        out.flush();
                    }
                }
                entry = zipInput.getNextEntry();
            }
        }
        logger.log(Level.FINEST, "Extracted zip file to temp directory {0}", tempDirectory.toString());
        return tempDirectory;
    }

    private void backupDomains() throws CommandException {
        logger.log(Level.INFO, "Backing up domain configs");
        File[] domaindirs = getDomainsDir().listFiles(File::isDirectory);
        for (File domaindir : domaindirs) {
            CLICommand backupDomainCommand = CLICommand.getCommand(habitat, "backup-domain");
            if (StringUtils.ok(domainDirParam)) {
                logger.log(Level.FINE, "Executing command: {0}", "backup-domain --domaindir "
                        + domainDirParam + " " + domaindir.getName());
                backupDomainCommand.execute("backup-domain", "--domaindir", domainDirParam, domaindir.getName());
            } else {
                logger.log(Level.FINE, "Executing command: {0}", "backup-domain " + domaindir.getName());
                backupDomainCommand.execute("backup-domain", domaindir.getName());
            }
        }
    }

    private void cleanupExisting() throws IOException {
        logger.log(Level.FINE, "Deleting old server backup if present");
        DeleteFileVisitor visitor = new DeleteFileVisitor();
        for (String folder : moveFolders) {
            Path folderPath = Paths.get(glassfishDir, folder + ".old");
            // Only attempt to delete folders which exist
            // Don't fail out if it doesn't exist, just keep going - we want to delete all we can
            if (folderPath.toFile().exists()) {
                Files.walkFileTree(folderPath, visitor);
            } else {
                logger.log(Level.FINER, "No old install directory found for {0}, skipping", folderPath.toString());
            }
        }
        logger.log(Level.FINE, "Deleted old server backup");
        deleteStagedInstall();
    }

    private void moveFiles(Path newVersion) throws IOException {
        if (!stage) {
            logger.log(Level.FINE, "Moving files to old");
            for (String folder : moveFolders) {
                try {
                    // Just attempt to move all folders - any exceptions aside from NoSuchFile on an MQ directory
                    // are unexpected and we should cancel out if we hit one
                    Path currentFile = Paths.get(glassfishDir, folder);
                    Path oldFile = Paths.get(glassfishDir, folder + ".old");
                    logger.log(Level.FINER, "Moving current install file {0} to old directory {1}",
                            new Object[]{currentFile.toString(), oldFile.toString()});
                    Files.move(currentFile, oldFile, StandardCopyOption.REPLACE_EXISTING);
                    logger.log(Level.FINEST, "Moved current install file {0} to old directory {1}",
                            new Object[]{currentFile.toString(), oldFile.toString()});
                } catch (NoSuchFileException nsfe) {
                    if (nsfe.getMessage().contains("glassfish" + File.separator + ".." + File.separator + "mq")
                            && isWebDistributionUpgrade) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for mq directory under assumption " +
                                "this is a payara-web distribution. Continuing to move files...");
                    } else if (nsfe.getMessage().contains("glassfish" + File.separator + "h2db")) {
                        logger.log(Level.FINE, "Ignoring NoSuchFileException for h2db directory under assumption " +
                                "this is a distribution without the duplicate directory...");
                    } else {
                        throw nsfe;
                    }
                }
            }
        }
        logger.log(Level.FINE, "Moved files to old");

        moveExtracted(newVersion);
    }

    private void moveExtracted(Path newVersion) throws IOException {
        logger.log(Level.FINE, "Copying extracted files");

        for (String folder : moveFolders) {
            Path sourcePath = newVersion.resolve(
                    "payara" + getUpgradeMajorVersion() + File.separator + "glassfish" + File.separator + folder);

            // Check that the path actually exists - some folders may have moved or be different between versions
            // For example some versions of Payara 5 have a duplicate glassfish/h2db directory
            if (!Files.exists(sourcePath)) {
                if (sourcePath.endsWith("glassfish" + File.separator + "h2db")) {
                    logger.log(Level.FINER,
                            "Source path {0} doesn't exist, skipping under assumption this is a distribution without the duplicate directory...",
                            sourcePath.toString());
                    continue;
                }

                // Check for the usual MQ missing in payara-web case
                if (sourcePath.endsWith("glassfish" + File.separator + ".." + File.separator + "mq")
                        && isWebDistributionUpgrade) {
                    logger.log(Level.FINER,
                            "Source path {0} doesn't exist, skipping under assumption this is a payara-web distribution...",
                            sourcePath.toString());
                    continue;
                }
            }

            Path targetPath = Paths.get(glassfishDir, folder);
            if (stage) {
                targetPath = Paths.get(targetPath + ".new");
            }
            logger.log(Level.FINER, "Moving extracted file {0} to {1}",
                    new Object[]{sourcePath.toString(), targetPath.toString()});

            if (Paths.get(glassfishDir, folder).toFile().isDirectory()) {
                if (!targetPath.toFile().exists()) {
                    logger.log(Level.FINER, "Target path {0} doesn't exist, creating it.", targetPath.toString());
                    Files.createDirectory(targetPath);
                    logger.log(Level.FINEST, "Created target path {0}", targetPath.toString());
                }
            }

            // osgi-cache directory doesn't exist in a new Payara install so can't be copied and should be ignored.
            if (!folder.contains("osgi-cache")) {
                CopyFileVisitor visitor = new CopyFileVisitor(sourcePath, targetPath);
                Files.walkFileTree(sourcePath, visitor);
            }
        }
        logger.log(Level.FINE, "Extracted files copied");
    }

    private void undoMoveFiles() throws IOException {
        // We don't know the state of the "current" or "old" installs, so we need to do this file by file with
        // a visitor that overwrites rather than doing it by folder with Files.move since Files.move would
        // require us to deal with DirectoryNotEmptyExceptions
        logger.log(Level.FINE, "Moving old back");
        for (String folder : moveFolders) {
            try {
                Path movedToPath = Paths.get(glassfishDir, folder + ".old");

                // Skip this folder if we don't appear to have moved it
                if (!movedToPath.toFile().exists()) {
                    logger.log(Level.FINEST, "Moved to path {0} doesn't exist, skipping", movedToPath.toString());
                    continue;
                }

                // Copy the files from the folder, overwriting any
                Path movedFromPath = Paths.get(glassfishDir, folder);
                CopyFileVisitor copyVisitor = new CopyFileVisitor(movedToPath, movedFromPath);
                logger.log(Level.FINER, "Copying files from {0} to {1}",
                        new Object[]{movedToPath.toString(), movedFromPath.toString()});
                Files.walkFileTree(movedToPath, copyVisitor);
                logger.log(Level.FINEST, "Copied files from {0} to {1}",
                        new Object[]{movedToPath.toString(), movedFromPath.toString()});

                // Clear out the leftover "old" install
                DeleteFileVisitor deleteVisitor = new DeleteFileVisitor();
                logger.log(Level.FINER, "Deleting files from {0}", movedToPath.toString());
                Files.walkFileTree(movedToPath, deleteVisitor);
                logger.log(Level.FINEST, "Deleted files from {0}", movedToPath.toString());
            } catch (NoSuchFileException nsfe) {
                // Don't exit out on NoSuchFileExceptions, just keep going - any NoSuchFileException is likely
                // just a case of the file not having been moved yet.
                logger.log(Level.FINE, "Ignoring NoSuchFileException for directory {0} under assumption " +
                        "it hasn't been moved yet. Continuing rollback...", folder + ".old");
            }
        }
        logger.log(Level.FINE, "Moved old back");
    }

    private void fixPermissions() throws IOException {
        logger.log(Level.FINE, "Fixing file permissions");
        // Fix the permissions of any bin directories in moveFolders
        fixBinDirPermissions();
        // Fix the permissions of nadmin (since it's not in a bin directory)
        fixNadminPermissions();
        logger.log(Level.FINE, "File permissions fixed");
    }

    private void fixBinDirPermissions() throws IOException {
        for (String folder : moveFolders) {
            BinDirPermissionFileVisitor visitor = new BinDirPermissionFileVisitor();
            if (stage) {
                Files.walkFileTree(Paths.get(glassfishDir, folder + ".new"), visitor);
            } else {
                Files.walkFileTree(Paths.get(glassfishDir, folder), visitor);
            }
        }
    }

    private void fixNadminPermissions() throws IOException {
        // Check that we're actually upgrading the payara(5/6)/glassfish/lib directory before messing with permissions
        if (Arrays.stream(moveFolders).anyMatch(folder -> folder.equals("lib"))) {
            Path nadminPath = Paths.get(glassfishDir, "lib", "nadmin");
            if (stage) {
                nadminPath = Paths.get(glassfishDir, "lib.new", "nadmin");
            }

            if (nadminPath.toFile().exists()) {
                logger.log(Level.FINER, "Fixing file permissions for {0} to {1}",
                        new Object[]{nadminPath.toString(), PERMISSIONS});
                Files.setPosixFilePermissions(nadminPath, PosixFilePermissions.fromString(PERMISSIONS));
                logger.log(Level.FINEST, "Fixed file permissions for {0} to {1}",
                        new Object[]{nadminPath.toString(), PERMISSIONS});
            } else {
                logger.log(Level.FINER, "File {0} does not exist, skipping", nadminPath.toString());
            }

            Path nadminBatPath = Paths.get(glassfishDir, "lib", "nadmin.bat");
            if (stage) {
                nadminBatPath = Paths.get(glassfishDir, "lib.new", "nadmin.bat");
            }

            if (nadminBatPath.toFile().exists()) {
                logger.log(Level.FINER, "Fixing file permissions for {0} to {1}",
                        new Object[]{nadminBatPath.toString(), PERMISSIONS});
                Files.setPosixFilePermissions(nadminBatPath, PosixFilePermissions.fromString(PERMISSIONS));
                logger.log(Level.FINEST, "Fixed file permissions for {0} to {1}",
                        new Object[]{nadminBatPath.toString(), PERMISSIONS});
            } else {
                logger.log(Level.FINER, "File {0} does not exist, skipping", nadminBatPath.toString());
            }
        }
    }

    private class BinDirPermissionFileVisitor implements FileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            // Since MOVEDIRS only contains the top-level directory of what we want to upgrade (e.g. mq), checking
            // whether the name is equal to "bin" before skipping subtrees here is too heavy-handed
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            // If we're not in a bin directory, skip
            if (file.getParent().getFileName().toString().equals("bin") ||
                    file.getParent().getFileName().toString().equals("bin.new")) {
                if (!OS.isWindows()) {
                    logger.log(Level.FINER, "Fixing file permissions for {0} to {1}",
                            new Object[]{file.toString(), PERMISSIONS});
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(PERMISSIONS));
                    logger.log(Level.FINEST, "Fixed file permissions for {0} to {1}",
                            new Object[]{file.toString(), PERMISSIONS});
                } else {
                    logger.log(Level.FINER,
                            "OS is Windows, skipping fixing file permissions for {0}", file.toString());
                }

                return FileVisitResult.CONTINUE;
            } else {
                logger.log(Level.FINER, "File {0} is not in a bin directory, skipping", file.getParent().toString());
            }

            return FileVisitResult.SKIP_SIBLINGS;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            // We can't nicely check if the "new" installation is a web distribution or not ("distribution" param is
            // optional with "useDownloaded"), so specifically catch a NSFE for the MQ directory.
            if (exc instanceof NoSuchFileException
                    && exc.getMessage().contains("glassfish" + File.separator + ".." + File.separator + "mq")
                    && isWebDistributionUpgrade) {
                logger.log(Level.FINE, "Ignoring NoSuchFileException for mq directory under assumption " +
                        "this is a payara-web distribution. Continuing fixing of permissions...");
                return FileVisitResult.SKIP_SUBTREE;
            }
            // osgi-cache directory doesn't exist in a new Payara install so can be safely ignored
            if (exc instanceof NoSuchFileException && exc.getMessage().contains("osgi-cache")) {
                logger.log(Level.FINE,
                        "Ignoring NoSuchFileException for osgi-cache directory. Continuing fixing of permissions...");
                return FileVisitResult.SKIP_SUBTREE;
            }
            // glassfish/h2db directory doesn't exist in a Payara 6 install so can be safely ignored
            if (exc instanceof NoSuchFileException && exc.getMessage().contains("glassfish/h2db")) {
                logger.log(Level.FINE,
                        "Ignoring NoSuchFileException for glassfish/h2db directory under assumption this is a " +
                                "distribution without this duplicate directory. Continuing fixing of permissions...");
                return FileVisitResult.SKIP_SUBTREE;
            }

            logger.log(Level.SEVERE, "File could not visited: {0}", file.toString());
            throw exc;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Class that calculates the default parameter value for --stage. To preserve the original functionality, the stage
     * param would have a default value of false. Since in-place upgrades aren't supported on Windows due to
     * file-locking, and so that a user doesn't have to always specify --stage=true if on Windows, this calculator makes
     * the default value true if on Windows.
     */
    public static class DefaultStageParamCalculator extends ParamDefaultCalculator {

        @Override
        public String defaultValue(ExecutionContext context) {
            return Boolean.toString(OS.isWindows());
        }
    }
}
