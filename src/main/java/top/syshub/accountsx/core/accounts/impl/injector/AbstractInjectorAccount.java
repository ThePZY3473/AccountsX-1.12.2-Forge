package top.syshub.accountsx.core.accounts.impl.injector;

import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.model.AccountType;

import java.util.UUID;

public abstract class AbstractInjectorAccount extends BaseAccount {
    private String server;

    private volatile String loginToken;

    private volatile String preferredPlayerUUID;

    private volatile String clientToken;

    public AbstractInjectorAccount(String accessToken, String playerName, UUID playerUUID, String server, String preferredPlayerUUID, AccountType type, String accountName, String avatar) {
        this(accessToken, playerName, playerUUID, server, preferredPlayerUUID, null, type, accountName, avatar);
    }

    public AbstractInjectorAccount(String accessToken, String playerName, UUID playerUUID, String server, String preferredPlayerUUID,
                                   String clientToken, AccountType type, String accountName, String avatar) {
        super(accessToken, playerName, playerUUID, type, accountName, avatar);
        this.server = server;
        this.loginToken = accessToken;
        this.preferredPlayerUUID = preferredPlayerUUID;
        this.clientToken = clientToken;
    }

    public final String getServer() {
        return server;
    }

    public final String getLoginToken() {
        return loginToken;
    }

    public final String getPreferredPlayerUUID() {
        return preferredPlayerUUID;
    }

    public final String getClientToken() {
        return clientToken;
    }

    public final void setLoginProfile(String loginToken, String preferredPlayerUUID) {
        setLoginProfile(loginToken, preferredPlayerUUID, clientToken);
    }

    public final void setLoginProfile(String loginToken, String preferredPlayerUUID, String clientToken) {
        this.loginToken = loginToken;
        this.preferredPlayerUUID = preferredPlayerUUID;
        this.clientToken = clientToken;
    }
}
