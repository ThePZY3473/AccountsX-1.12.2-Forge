package top.syshub.accountsx.forge.mc;

import com.mojang.authlib.properties.PropertyMap;
import top.syshub.accountsx.core.accounts.AccountUUID;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.impl.env.EnvironmentAccount;
import top.syshub.accountsx.core.adapters.api.MinecraftAdapter;
import top.syshub.accountsx.forge.authlib.AccountSessionImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.Session;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Proxy;
import java.net.URI;

public final class MinecraftAdapterImpl implements MinecraftAdapter<AccountSessionImpl> {
    @Override
    public EnvironmentAccount fromCurrentClient() {
        Session session = Minecraft.getMinecraft().getSession();
        return new EnvironmentAccount(session.getToken(), session.getUsername(), AccountUUID.parse(session.getPlayerID()));
    }

    @Override
    public <T extends BaseAccount> void switchAccount(AccountSessionImpl session) {
        Minecraft client = Minecraft.getMinecraft();
        BaseAccount.AccountStorage storage = session.storage();

        Session mcSession = new Session(
                storage.getPlayerName(),
                AccountUUID.toMinecraftStyleString(storage.getPlayerUUID()),
                storage.getAccessToken(),
                "mojang"
        );

        setMinecraftField(client, mcSession, "session", "field_71449_j");
        setMinecraftField(client, session.sessionService(), "sessionService", "field_152355_az");
        setMinecraftField(client, new SkinManager(client.getTextureManager(), getSkinCacheDirectory(client), session.sessionService()), "skinManager", "field_152350_aA");

        PropertyMap properties = getMinecraftField(client, "profileProperties", "field_181038_N");
        if (properties != null) {
            properties.clear();
            properties.putAll(session.gameProfile().getProperties());
        }
    }

    @Override
    public Proxy getGameProxy() {
        return Minecraft.getMinecraft().getProxy();
    }

    @Override
    public Thread getMinecraftClientThread() {
        return getMinecraftField(Minecraft.getMinecraft(), "mcThread", "field_152352_aC");
    }

    @Override
    public void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                Sys.openURL(url);
            }
        } catch (Throwable t) {
            Sys.openURL(url);
        }
    }

    @Override
    public void crash(final RuntimeException e) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                throw e;
            }
        });
    }

    @Override
    public void copyText(final String text) {
        Minecraft client = Minecraft.getMinecraft();
        if (client.isCallingFromMinecraftThread()) {
            try {
                GuiScreen.setClipboardString(text);
            } catch (Throwable ignored) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            }
        } else {
            client.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    copyText(text);
                }
            });
        }
    }

    @Override
    public void showToast(String title, String description, Object... args) {
        top.syshub.accountsx.forge.ForgeClientEvents.setStatusMessage(title, description, args);
    }

    private static void setMinecraftField(Minecraft client, Object value, String... names) {
        try {
            Field field = null;
            for (String name : names) {
                try {
                    field = ObfuscationReflectionHelper.findField(Minecraft.class, name);
                    break;
                } catch (Exception ignored) {
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(java.util.Arrays.toString(names));
            }
            field.setAccessible(true);

            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.set(client, value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot update Minecraft session field.", e);
        }
    }

    private static File getSkinCacheDirectory(Minecraft client) {
        File assetsDirectory = getMinecraftField(client, "fileAssets", "field_110446_Y");
        return new File(assetsDirectory, "skins");
    }

    @SuppressWarnings("unchecked")
    private static <T> T getMinecraftField(Minecraft client, String... names) {
        try {
            return (T) ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, client, names);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read Minecraft field.", e);
        }
    }
}
