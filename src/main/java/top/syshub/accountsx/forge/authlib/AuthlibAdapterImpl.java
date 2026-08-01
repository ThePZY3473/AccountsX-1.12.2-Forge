package top.syshub.accountsx.forge.authlib;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.model.context.AccountContext;
import top.syshub.accountsx.core.accounts.impl.microsoft.MicrosoftConstants;
import top.syshub.accountsx.core.adapters.api.AuthlibAdapter;

import java.io.IOException;
import java.net.Proxy;
import java.util.UUID;

public final class AuthlibAdapterImpl implements AuthlibAdapter<AccountSessionImpl> {
    @Override
    public AccountSessionImpl createAccountProfile(BaseAccount.AccountStorage storage, AccountContext context, Proxy proxy) throws IOException {
        YggdrasilAuthenticationService service = new YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString());
        MinecraftSessionService defaultSessionService = service.createMinecraftSessionService();
        MinecraftSessionService sessionService = isCustomSessionServer(context)
                ? new InjectorMinecraftSessionService(context, proxy)
                : defaultSessionService;
        if (sessionService instanceof InjectorMinecraftSessionService) {
            AccountsX.LOGGER.info("Using AccountsX runtime Yggdrasil session service for {}.", context.server().sessionURL());
        }
        GameProfile profile = sessionService.fillProfileProperties(new GameProfile(storage.getPlayerUUID(), storage.getPlayerName()), false);
        return new AccountSessionImpl(storage, service, sessionService, profile);
    }

    private static boolean isCustomSessionServer(AccountContext context) {
        if (context == null || context.server() == null || context.server().sessionURL() == null) {
            return false;
        }
        return !MicrosoftConstants.SESSION.equals(stripTrailingSlash(context.server().sessionURL()));
    }

    private static String stripTrailingSlash(String url) {
        while (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
