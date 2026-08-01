package top.syshub.accountsx.forge.gui;

import top.syshub.accountsx.core.ui.Memory;
import top.syshub.accountsx.core.utils.Threading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultMemory implements Memory {
    private final GuiScreen loginScreen;
    private final Map<String, Object> objects = new ConcurrentHashMap<String, Object>();

    public DefaultMemory(GuiScreen loginScreen) {
        this.loginScreen = loginScreen;
    }

    @Override
    public <T> void set(String guid, T value) {
        objects.put(guid, value);
    }

    @Override
    public <T> T get(String guid, Class<T> type) {
        return type.cast(objects.get(guid));
    }

    @Override
    @Threading.Thread(Threading.WORKER)
    public boolean isScreenClosed() {
        Threading.checkAccountWorkerThread();
        return Minecraft.getMinecraft().currentScreen != loginScreen;
    }
}
