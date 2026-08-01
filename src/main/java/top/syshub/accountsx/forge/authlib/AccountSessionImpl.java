package top.syshub.accountsx.forge.authlib;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.adapters.api.AccountSession;

public final class AccountSessionImpl implements AccountSession {
    private final BaseAccount.AccountStorage storage;
    private final YggdrasilAuthenticationService authenticationService;
    private final MinecraftSessionService sessionService;
    private final GameProfile gameProfile;

    public AccountSessionImpl(BaseAccount.AccountStorage storage,
                              YggdrasilAuthenticationService authenticationService,
                              MinecraftSessionService sessionService,
                              GameProfile gameProfile) {
        this.storage = storage;
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.gameProfile = gameProfile;
    }

    public BaseAccount.AccountStorage storage() {
        return storage;
    }

    public YggdrasilAuthenticationService authenticationService() {
        return authenticationService;
    }

    public MinecraftSessionService sessionService() {
        return sessionService;
    }

    public GameProfile gameProfile() {
        return gameProfile;
    }
}
