package top.syshub.accountsx.forge;

import top.syshub.accountsx.core.manager.AccountManager;
import top.syshub.accountsx.forge.gui.AccountScreen;
import top.syshub.accountsx.forge.gui.I18N;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ForgeClientEvents {
    private static final int ACCOUNT_BUTTON_ID = 0xACC0;
    private static volatile String statusMessage;
    private static volatile long statusMessageUntil;

    public static void setStatusMessage(String title, String description, Object... args) {
        String message = I18N.TRANSLATOR.translate(title);
        if (description != null) {
            message = message + ": " + I18N.TRANSLATOR.translate(description, stringArgs(args));
        }
        statusMessage = message;
        statusMessageUntil = System.currentTimeMillis() + 8000L;
    }

    private static String[] stringArgs(Object[] args) {
        String[] result = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = String.valueOf(args[i]);
        }
        return result;
    }

    public static String getStatusMessage() {
        if (System.currentTimeMillis() > statusMessageUntil) {
            return null;
        }
        return statusMessage;
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (gui instanceof GuiMainMenu) {
            event.getButtonList().add(new GuiButton(
                    ACCOUNT_BUTTON_ID,
                    gui.width / 2 + 104,
                    gui.height / 4 + 48 + 24 * 2,
                    40,
                    20,
                    "AX"
            ));
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getGui() instanceof GuiMainMenu && event.getButton().id == ACCOUNT_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new AccountScreen(event.getGui()));
        }
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof GuiMainMenu && AccountManager.getCurrentAccount() != null) {
            String text = I18N.TRANSLATOR.translate(AccountManager.getCurrentAccount());
            event.getGui().drawCenteredString(
                    Minecraft.getMinecraft().fontRenderer,
                    text,
                    event.getGui().width / 2,
                    15,
                    0xFFFFFF
            );

            String status = getStatusMessage();
            if (status != null) {
                event.getGui().drawCenteredString(
                        Minecraft.getMinecraft().fontRenderer,
                        status,
                        event.getGui().width / 2,
                        28,
                        0xFFFFA0
                );
            }
        }
    }
}
