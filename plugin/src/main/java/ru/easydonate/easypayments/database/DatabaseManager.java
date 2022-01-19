package ru.easydonate.easypayments.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.easydonate.easypayments.database.model.Customer;
import ru.easydonate.easypayments.database.model.Purchase;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class DatabaseManager {

    private final Logger logger;
    private final ConnectionSource connectionSource;
    private final ExecutorService asyncExecutorService;

    private final Dao<Customer, String> customersDao;
    private final Dao<Purchase, Integer> purchasesDao;

    public DatabaseManager(@NotNull Plugin plugin, @NotNull Database database) throws SQLException {
        this.logger = plugin.getLogger();
        this.connectionSource = database.establishConnection();
        this.asyncExecutorService = Executors.newCachedThreadPool();

        this.customersDao = DaoManager.createDao(connectionSource, Customer.class);
        this.purchasesDao = DaoManager.createDao(connectionSource, Purchase.class);
    }

    public void shutdown() {
        if(asyncExecutorService != null)
            asyncExecutorService.shutdown();

        if(connectionSource != null)
            connectionSource.closeQuietly();
    }

    // --- customers
    public @NotNull CompletableFuture<Customer> getCustomer(@NotNull String playerName) {
        return supplyAsync(() -> customersDao.queryForId(playerName));
    }

    public @NotNull CompletableFuture<Customer> getCustomerByUUID(@NotNull UUID playerUUID) {
        return supplyAsync(() -> customersDao.queryBuilder()
                .where()
                .eq(Customer.COLUMN_PLAYER_UUID, playerUUID.toString())
                .queryForFirst());
    }

    public @NotNull CompletableFuture<Customer> getOrCreateCustomer(@NotNull OfflinePlayer bukkitPlayer) {
        return getCustomer(bukkitPlayer.getName()).thenApply(customer -> {
            if(customer == null) {
                customer = new Customer(bukkitPlayer);
                saveCustomer(customer).join();
            }

            return customer;
        });
    }

    public @NotNull CompletableFuture<Void> saveCustomer(@NotNull Customer customer) {
        return runAsync(() -> customersDao.createOrUpdate(customer));
    }

    // --- purchases
    public @NotNull CompletableFuture<Purchase> getPurchase(@NotNull int purchaseId) {
        return supplyAsync(() -> purchasesDao.queryForId(purchaseId));
    }

    public @NotNull CompletableFuture<Void> savePurchase(@NotNull Purchase purchase) {
        return runAsync(() -> purchasesDao.createOrUpdate(purchase));
    }

    // --- async proxied methods
    private @NotNull CompletableFuture<Void> runAsync(@NotNull ThrowableRunnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (SQLException ex) {
                handleThrowable(ex);
            }
        }, asyncExecutorService);
    }

    private <T> @NotNull CompletableFuture<T> supplyAsync(@NotNull ThrowableSupplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.supply();
            } catch (SQLException ex) {
                handleThrowable(ex);
                return null;
            }
        }, asyncExecutorService);
    }

    private void handleThrowable(@NotNull Throwable throwable) {
        logger.severe("An error has occurred when this plugin tried to handle an SQL statement!");
        logger.severe(throwable.toString());
    }

    @FunctionalInterface
    private interface ThrowableRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface ThrowableSupplier<T> {
        @Nullable T supply() throws SQLException;
    }

}
