package top.syshub.accountsx.core;

import top.syshub.accountsx.core.accounts.impl.microsoft.MicrosoftConstants;
import top.syshub.accountsx.core.manager.AccountManager;
import top.syshub.accountsx.forge.ForgeClientEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = AccountsX.MOD_ID,
        name = AccountsX.MOD_NAME,
        version = AccountsX.VERSION,
        useMetadata = true,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]"
)
public class AccountsX {
    public static final String MC_ADAPTER_ID = "accountsx-adapter-mc";
    public static final String AUTHLIB_ADAPTER_ID = "accountsx-adapter-authlib";
    public static final String MOD_ID = "accountsx";
    public static final String MOD_NAME = "Accounts X";
    public static final String VERSION = "1.12.2-Forge";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void onInitialize(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ForgeClientEvents());
        AccountManager.initialize();
        MicrosoftConstants.initialize();
    }
}
