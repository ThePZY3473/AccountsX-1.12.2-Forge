package top.syshub.accountsx.forge.authlib;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.model.context.AccountContext;
import top.syshub.accountsx.core.adapters.api.AuthlibAdapter;

import java.io.IOException;
import java.net.Proxy;
import java.util.UUID;

public final class AuthlibAdapterImpl implements AuthlibAdapter<AccountSessionImpl> {
    @Override
    public AccountSessionImpl createAccountProfile(BaseAccount.AccountStorage storage, AccountContext context, Proxy proxy) throws IOException {
        YggdrasilAuthenticationService service = new YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString());
        MinecraftSessionService defaultSessionService = service.createMinecraftSessionService();
        MinecraftSessionService sessionService = hasSessionServer(context)
                ? new InjectorMinecraftSessionService(context, proxy)
                : defaultSessionService;
        if (sessionService instanceof InjectorMinecraftSessionService) {
            AccountsX.LOGGER.info("Using AccountsX runtime Yggdrasil session service for {}.", context.server().sessionURL());
        }
        GameProfile profile = sessionService.fillProfileProperties(new GameProfile(storage.getPlayerUUID(), storage.getPlayerName()), false);
        return new AccountSessionImpl(storage, service, sessionService, profile);
    }

    private static boolean hasSessionServer(AccountContext context) {
        if (context == null || context.server() == null || context.server().sessionURL() == null) {
            return false;
        }
        return !context.server().sessionURL().trim().isEmpty();
    }
}
