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
        if (context != null) {
            AccountsX.LOGGER.warn("Minecraft 1.12.2 uses the legacy authlib API; custom Yggdrasil endpoints require authlib-injector or an equivalent runtime transformer.");
        }

        YggdrasilAuthenticationService service = new YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString());
        MinecraftSessionService sessionService = service.createMinecraftSessionService();
        GameProfile profile = sessionService.fillProfileProperties(new GameProfile(storage.getPlayerUUID(), storage.getPlayerName()), false);
        return new AccountSessionImpl(storage, service, sessionService, profile);
    }
}
