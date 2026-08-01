package top.syshub.accountsx.core.manager;

import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.accounts.AccountProvider;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.impl.injector.AbstractInjectorAccount;
import top.syshub.accountsx.core.accounts.impl.offline.OfflineAccount;
import top.syshub.accountsx.core.accounts.model.AccountState;
import top.syshub.accountsx.core.accounts.model.AccountType;
import top.syshub.accountsx.core.accounts.model.PlayerNoLongerExistedException;
import top.syshub.accountsx.core.adapters.Adapters;
import top.syshub.accountsx.core.adapters.api.AccountSession;
import top.syshub.accountsx.core.manager.config.ConfigHandle;
import top.syshub.accountsx.core.utils.Threading;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public final class AccountManager {
    private static final List<BaseAccount> accounts = new CopyOnWriteArrayList<>();
    private static final List<BaseAccount> readonlyAccounts = Collections.unmodifiableList(accounts);
    private static volatile BaseAccount current = null;

    private AccountManager() {
    }

    public static List<BaseAccount> getAccountsView() {
        return readonlyAccounts;
    }

    public static BaseAccount getCurrentAccount() {
        return current;
    }

    public static void initialize() {
        accounts.add(current = Adapters.getMinecraftAdapter().fromCurrentClient());

        accounts.addAll(ConfigHandle.load());

        List<BaseAccount> toRefresh = new ArrayList<>();
        for (BaseAccount account : accounts) {
            if (account.getAccountStorage().getState() != AccountState.AUTHORIZED || isProfileIncomplete(account)) {
                toRefresh.add(account);
            }
        }

        if (!toRefresh.isEmpty()) {
            AccountWorker.submit(() -> refreshAccountsParallel(toRefresh));
        }

        save();
    }

    @Threading.Thread(Threading.CLIENT)
    public static void dropAccount(BaseAccount account) {
        Threading.checkMinecraftClientThread();

        if (account.getAccountType() == AccountType.ENV_DEFAULT) {
            return;
        }

        accounts.remove(account);
        save();
    }

    @Threading.Thread(Threading.CLIENT)
    public static void addAccount(BaseAccount account) {
        Threading.checkMinecraftClientThread();

        accounts.add(account);
        save();
    }

    @Threading.Thread(Threading.CLIENT)
    public static void moveAccount(BaseAccount account, int index) {
        Threading.checkMinecraftClientThread();

        accounts.remove(account);
        accounts.add(index, account);
        save();
    }

    @Threading.Thread(Threading.WORKER)
    public static AccountSession loginAccount(BaseAccount account) throws IOException {
        Threading.checkAccountWorkerThread();

        if (account.getAccountStorage().getState() != AccountState.AUTHORIZED || isProfileIncomplete(account)) {
            refreshAccount(account, true);

            save();
        }

        if (isProfileIncomplete(account)) {
            throw new IOException("Cannot switch account because the saved profile is incomplete: " + describeProfile(account));
        }

        return Adapters.getAuthlibAdpater().createAccountProfile(
                account.getAccountStorage(),
                AccountProvider.getProvider(account).createAccountContext(account),
                Adapters.getMinecraftAdapter().getGameProxy()
        );
    }

    @Threading.Thread(Threading.CLIENT)
    public static void switchAccount(BaseAccount account, AccountSession session) {
        Threading.checkMinecraftClientThread();

        current = account;
        Adapters.getMinecraftAdapter().switchAccount(session);
    }

    @Threading.Thread(Threading.WORKER)
    private static void refreshAccount(BaseAccount account, boolean thrown) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            account.setProfileState(AccountState.UNAUTHORIZED);
            if (thrown) {
                throw new IOException("Interrupted");
            } else {
                return;
            }
        }
        account.setProfileState(AccountState.AUTHORIZING);
        try {
            AccountProvider.getProvider(account).refresh(account);
        } catch (IOException e) {
            account.setProfileState(AccountState.UNAUTHORIZED);
            if (thrown) {
                throw e;
            } else {
                AccountsX.LOGGER.error("Cannot refresh the account.", e);
                return;
            }
        }

        if (account.getAccountStorage().getState() != AccountState.AUTHORIZED) {
            throw new IOException("Account provider " + account.getAccountType() + " has finished it's refresh invocation, but neither an exception was thrown nor set the account storage to AUTHORIZED");
        }
    }

    @Threading.Thread(Threading.WORKER)
    private static void refreshAccountsParallel(List<BaseAccount> toRefresh) {
        CountDownLatch latch = new CountDownLatch(toRefresh.size());
        List<Thread> threads = new ArrayList<>(toRefresh.size());
        for (BaseAccount account : toRefresh) {
            final Thread t = getThread(account, latch);
            threads.add(t);
            t.start();
        }

        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                for (Thread t : threads) {
                    t.interrupt();
                }
                Thread.currentThread().interrupt();
            }
        }
        save();
    }

    private static Thread getThread(BaseAccount account, CountDownLatch latch) {
        Thread t = new Thread(null, () -> {
            AccountWorker.registerWorkerThread(Thread.currentThread());
            try {
                try {
                    refreshAccount(account, false);
                } catch (Throwable t1) {
                    AccountsX.LOGGER.warn("An exception has occurred in AccountsX Background Thread.", t1);
                    Adapters.getMinecraftAdapter().showToast("accountsx.account.fail.title", AccountManager.handleException(t1));
                }
            } finally {
                AccountWorker.unregisterWorkerThread(Thread.currentThread());
                latch.countDown();
            }
        }, "AccountsX Background Worker Thread - parallel-" + account.getAccountName());
        t.setDaemon(true);
        return t;
    }

    private static boolean isProfileIncomplete(BaseAccount account) {
        if (account.getAccountType() == AccountType.ENV_DEFAULT) {
            return false;
        }
        BaseAccount.AccountStorage storage = account.getAccountStorage();
        return storage == null ||
                storage.getAccessToken() == null || storage.getAccessToken().isEmpty() ||
                storage.getPlayerName() == null || storage.getPlayerName().isEmpty() ||
                storage.getPlayerUUID() == null;
    }

    private static String describeProfile(BaseAccount account) {
        BaseAccount.AccountStorage storage = account.getAccountStorage();
        StringBuilder builder = new StringBuilder();
        builder.append("type=").append(account.getAccountType());
        builder.append(", storage=").append(storage == null ? "null" : "present");
        if (storage != null) {
            builder.append(", accessToken=").append(storage.getAccessToken() == null || storage.getAccessToken().isEmpty() ? "missing" : "present");
            builder.append(", playerName=").append(storage.getPlayerName() == null || storage.getPlayerName().isEmpty() ? "missing" : "present");
            builder.append(", playerUUID=").append(storage.getPlayerUUID() == null ? "missing" : "present");
            builder.append(", state=").append(storage.getState());
        }
        if (account instanceof AbstractInjectorAccount) {
            AbstractInjectorAccount injector = (AbstractInjectorAccount) account;
            builder.append(", server=").append(injector.getServer() == null || injector.getServer().isEmpty() ? "missing" : injector.getServer());
            builder.append(", loginToken=").append(injector.getLoginToken() == null || injector.getLoginToken().isEmpty() ? "missing" : "present");
            builder.append(", clientToken=").append(injector.getClientToken() == null || injector.getClientToken().isEmpty() ? "missing" : "present");
            builder.append(", preferredPlayerUUID=").append(injector.getPreferredPlayerUUID() == null || injector.getPreferredPlayerUUID().isEmpty() ? "missing" : "present");
        } else if (account instanceof OfflineAccount) {
            builder.append(", offlineAccount=true");
        }
        return builder.toString();
    }

    public static String handleException(Throwable t) {
        if (t instanceof PlayerNoLongerExistedException) {
            return "accountsx.account.fail.player_no_longer_existed";
        } else {
            return "accountsx.account.fail.unknown";
        }
    }

    private static void save() {
        try {
            ConfigHandle.write();
        } catch (IOException e) {
            AccountsX.LOGGER.warn("Cannot save the config file.", e);
        }
    }
}
