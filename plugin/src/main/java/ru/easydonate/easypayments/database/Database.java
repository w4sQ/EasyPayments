package ru.easydonate.easypayments.database;

import com.j256.ormlite.field.DataPersister;
import com.j256.ormlite.field.DataPersisterManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.easydonate.easypayments.config.Configuration;
import ru.easydonate.easypayments.database.credentials.DatabaseCredentials;
import ru.easydonate.easypayments.database.credentials.DatabaseCredentialsParser;
import ru.easydonate.easypayments.exception.CredentialsParseException;
import ru.easydonate.easypayments.exception.DriverLoadException;
import ru.easydonate.easypayments.exception.DriverNotFoundException;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Database {

    private final DatabaseType databaseType;
    private final DatabaseCredentials databaseCredentials;
    private final ConnectionSource bootstrapConnection;

    private final Set<Class<?>> registeredTables;

    public Database(@NotNull Plugin plugin, @NotNull Configuration config) throws
            CredentialsParseException,
            DriverNotFoundException,
            DriverLoadException,
            SQLException
    {
        this.registeredTables = new LinkedHashSet<>();

        String rawType = config.getString("database.type", "");
        this.databaseType = DatabaseType.getByKey(rawType);

        if(databaseType.isUnknown())
            throw new IllegalArgumentException(String.format("Unknown database type '%s'!", rawType));

        ConfigurationSection credentialsConfig = config.getSection("database." + databaseType.getKey());
        this.databaseCredentials = DatabaseCredentialsParser.parse(plugin, credentialsConfig, databaseType);
        this.databaseCredentials.loadDriver(plugin);

        this.bootstrapConnection = establishConnection();
    }

    public @NotNull DatabaseType getDatabaseType() {
        return databaseType;
    }

    public @NotNull ConnectionSource getBootstrapConnection() {
        if(bootstrapConnection == null)
            throw new IllegalStateException("The database instance wasn't initialized correctly!");

        return bootstrapConnection;
    }

    public @NotNull ConnectionSource establishConnection() throws SQLException {
        return databaseCredentials.getConnectionSource();
    }

    public @NotNull Database complete() throws SQLException {
        ConnectionSource bootstrapConnection = getBootstrapConnection();
        if(!registeredTables.isEmpty())
            for(Class<?> registeredTable : registeredTables)
                TableUtils.createTableIfNotExists(bootstrapConnection, registeredTable);

        bootstrapConnection.closeQuietly();
        return this;
    }

    public @NotNull Database registerTable(@NotNull Class<?> tableClass) {
        registeredTables.add(tableClass);
        return this;
    }

    public @NotNull Database registerPersister(@NotNull DataPersister dataPersister) {
        DataPersisterManager.registerDataPersisters(dataPersister);
        return this;
    }

}
