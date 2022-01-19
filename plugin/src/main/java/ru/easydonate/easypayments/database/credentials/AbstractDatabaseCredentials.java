package ru.easydonate.easypayments.database.credentials;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.easydonate.easypayments.crypto.MD5Checksum;
import ru.easydonate.easypayments.database.DatabaseType;
import ru.easydonate.easypayments.dependency.DependencyLoader;
import ru.easydonate.easypayments.dependency.PluginDependencyLoader;
import ru.easydonate.easypayments.exception.DriverLoadException;
import ru.easydonate.easypayments.exception.DriverNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractDatabaseCredentials implements DatabaseCredentials {

    protected final @NotNull DatabaseType databaseType;

    @CredentialField("params")
    protected List<String> parameters;

    protected String formatParameters() {
        if(parameters == null || parameters.isEmpty())
            return "";

        String joined = parameters.stream()
                .map(this::urlEncode)
                .collect(Collectors.joining("&"));

        return "?" + joined;
    }

    protected @NotNull String getDriverDownloadURL() {
        throw new UnsupportedOperationException("Not implemented by this credentials implementation!");
    }

    protected @NotNull String getDriverFileChecksum() {
        throw new UnsupportedOperationException("Not implemented by this credentials implementation!");
    }

    protected @NotNull String getDriverOutputFile() {
        throw new UnsupportedOperationException("Not implemented by this credentials implementation!");
    }

    protected void checkDriver(@NotNull Plugin plugin, boolean tryDownloadDriver) throws DriverNotFoundException, DriverLoadException {
        throw new UnsupportedOperationException("Not implemented by this credentials implementation!");
    }

    protected void checkDriver(@NotNull Plugin plugin, @NotNull String driverClass) throws DriverNotFoundException {
        try {
            Constructor<?> constructor = Class.forName(driverClass).getConstructor();
            constructor.newInstance();
        } catch (Exception ex) {
            throw new DriverNotFoundException(ex, databaseType);
        }
    }

    protected void tryDownloadDriver(@NotNull Plugin plugin) throws DriverNotFoundException, DriverLoadException {
        DependencyLoader dependencyLoader = PluginDependencyLoader.forPlugin(plugin);
        boolean loaded;

        try {
            File driverFile = downloadDriver(plugin);
            loaded = dependencyLoader.load(driverFile);

            if (loaded) {
                checkDriver(plugin, false);
                plugin.getLogger().info(databaseType.getName() + " JDBC Driver has been loaded into the JVM Runtime.");
            }
        } catch (DriverNotFoundException | DriverLoadException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new DriverLoadException(ex, databaseType);
        } catch (Exception ex) {
            throw new DriverNotFoundException(ex, databaseType);
        }

        if(!loaded)
            throw new DriverLoadException("Couldn't download the " + databaseType.getName() + " JDBC Driver!", databaseType);
    }

    protected @NotNull File downloadDriver(@NotNull Plugin plugin) throws IOException, DriverLoadException {
        File driverFolder = new File(plugin.getDataFolder(), "driver");
        driverFolder.mkdirs();

        DatabaseType databaseType = getDatabaseType();
        String driverDownloadURL = getDriverDownloadURL();
        String driverOutputFile = getDriverOutputFile();

        File destination = new File(driverFolder, driverOutputFile);
        if(destination.exists()) {
            if(!verifyMD5Hashsum(destination))
                throw new DriverLoadException("Failed to verify MD5 checksum! Please, try again.", databaseType);

            return destination;
        } else {
            destination.delete();
        }

        InputStream externalResource = new URL(driverDownloadURL).openConnection().getInputStream();
        if(externalResource == null)
            throw new DriverLoadException("External driver resource was not found!", databaseType);

        plugin.getLogger().info("Downloading " + databaseType.getName() + " JDBC driver...");
        plugin.getLogger().info("URL: " + driverDownloadURL);

        Files.copy(externalResource, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    private boolean verifyMD5Hashsum(@NotNull File downloadedFile) {
        try {
            String checksum = MD5Checksum.getMD5Checksum(downloadedFile);
            return checksum.equalsIgnoreCase(getDriverFileChecksum());
        } catch (Exception ignored) {
            return false;
        }
    }

    @SneakyThrows(UnsupportedEncodingException.class)
    private String urlEncode(String source) {
        return URLEncoder.encode(source, "UTF-8");
    }

}
