package ru.easydonate.easypayments.database.credentials.local;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.easydonate.easypayments.database.DatabaseType;
import ru.easydonate.easypayments.exception.DriverLoadException;
import ru.easydonate.easypayments.exception.DriverNotFoundException;

public final class H2DatabaseCredentials extends AbstractLocalDatabaseCredentials {

    public static final String DRIVER_DOWNLOAD_URL = "https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.0.206/h2-2.0.206.jar";
    public static final String DRIVER_FILE_CHECKSUM = "20862be3ea4d011d041ec80ca0ae92d3";
    public static final String DRIVER_OUTPUT_FILE = "h2.jar";

    public static final String DRIVER_CLASS = "org.h2.Driver";
    public static final String URL_PATTERN = "jdbc:h2:%s%s";

    public H2DatabaseCredentials(@NotNull Plugin plugin) {
        super(plugin, DatabaseType.H2);
    }

    @Override
    public @NotNull String getConnectionUrl() {
        return String.format(URL_PATTERN, getFilePath(), formatParameters());
    }

    @Override
    public void loadDriver(@NotNull Plugin plugin) throws DriverNotFoundException, DriverLoadException {
        checkDriver(plugin, true);
    }

    @Override
    protected @NotNull String getDriverDownloadURL() {
        return DRIVER_DOWNLOAD_URL;
    }

    @Override
    protected @NotNull String getDriverFileChecksum() {
        return DRIVER_FILE_CHECKSUM;
    }

    @Override
    protected @NotNull String getDriverOutputFile() {
        return DRIVER_OUTPUT_FILE;
    }

    @Override
    protected void checkDriver(@NotNull Plugin plugin, boolean tryDownloadDriver) throws DriverNotFoundException, DriverLoadException {
        try {
            checkDriver(plugin, DRIVER_CLASS);
            plugin.getLogger().info("H2 JDBC Driver is already loaded in the JVM Runtime.");
        } catch (DriverNotFoundException ex) {
            if(tryDownloadDriver) {
                tryDownloadDriver(plugin);
            } else {
                throw ex;
            }
        }

    }

}
