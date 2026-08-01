package top.syshub.accountsx.forge.gui;

import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.model.AccountType;
import top.syshub.accountsx.core.adapters.api.AccountSession;
import top.syshub.accountsx.core.manager.AccountManager;
import top.syshub.accountsx.core.manager.AccountWorker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;

public class AccountScreen extends GuiScreen {
    private static final int LAYOUT_HORIZONTAL_SPACING = 16;
    private static final int LAYOUT_VERTICAL_SPACING = 32;
    private static final int LAYOUT_BUTTON_H = 20;
    private static final int LAYOUT_TOOL_BAR_W = 150;
    private static final int LAYOUT_TOOL_BAR_SPACING = 20;
    private static final int LAYOUT_ENTRY_H = 36;
    private static final int CLOSE_BUTTON_ID = 1;
    private static final int ADD_ACCOUNT_BUTTON_ID = 100;

    private final GuiScreen parent;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;

    public AccountScreen(GuiScreen parent) {
        this.parent = parent;
    }

    public void syncAccounts() {
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.listLeft = LAYOUT_HORIZONTAL_SPACING + LAYOUT_TOOL_BAR_W + LAYOUT_TOOL_BAR_SPACING / 2 + 10;
        this.listRight = this.width - LAYOUT_HORIZONTAL_SPACING;
        this.listTop = LAYOUT_VERTICAL_SPACING + 20;
        this.listBottom = this.height - LAYOUT_VERTICAL_SPACING - 20;

        this.buttonList.add(new GuiButton(
                CLOSE_BUTTON_ID,
                LAYOUT_HORIZONTAL_SPACING,
                LAYOUT_VERTICAL_SPACING,
                LAYOUT_TOOL_BAR_W,
                LAYOUT_BUTTON_H,
                I18N.TRANSLATOR.translate("accountsx.general.action.close")
        ));

        int y = LAYOUT_VERTICAL_SPACING + LAYOUT_BUTTON_H + LAYOUT_BUTTON_H + 10;
        AccountType[] types = AccountType.CONFIGURABLE_VALUES;
        for (int i = 0; i < types.length; i++) {
            this.buttonList.add(new GuiButton(
                    ADD_ACCOUNT_BUTTON_ID + i,
                    LAYOUT_HORIZONTAL_SPACING,
                    y,
                    LAYOUT_TOOL_BAR_W,
                    LAYOUT_BUTTON_H,
                    I18N.TRANSLATOR.translate(types[i])
            ));
            y += LAYOUT_BUTTON_H;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == CLOSE_BUTTON_ID) {
            this.mc.displayGuiScreen(parent);
            return;
        }

        int accountTypeIndex = button.id - ADD_ACCOUNT_BUTTON_ID;
        if (accountTypeIndex >= 0 && accountTypeIndex < AccountType.CONFIGURABLE_VALUES.length) {
            this.mc.displayGuiScreen(UIScreenImpl.create(this, AccountType.CONFIGURABLE_VALUES[accountTypeIndex].getAccountProvider()));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = AccountWorker.isRunning()
                ? I18N.TRANSLATOR.translate("accountsx.account.general.operating")
                : I18N.TRANSLATOR.translate("accountsx.account.general.account_list");
        this.drawCenteredString(this.fontRenderer, title, this.width / 2 + listLeft / 2, LAYOUT_VERTICAL_SPACING, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, I18N.TRANSLATOR.translate(AccountManager.getCurrentAccount()), this.width / 2 + listLeft / 2, this.height - LAYOUT_VERTICAL_SPACING, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, I18N.TRANSLATOR.translate("accountsx.account.general.add_account"), LAYOUT_HORIZONTAL_SPACING + LAYOUT_TOOL_BAR_W / 2, LAYOUT_VERTICAL_SPACING + LAYOUT_BUTTON_H + LAYOUT_BUTTON_H, 0xFFFFFF);

        String status = top.syshub.accountsx.forge.ForgeClientEvents.getStatusMessage();
        if (status != null) {
            this.drawCenteredString(this.fontRenderer, status, this.width / 2 + listLeft / 2, LAYOUT_VERTICAL_SPACING + 12, 0xFFFFA0);
        }

        renderAccounts(mouseX, mouseY);
    }

    private void renderAccounts(int mouseX, int mouseY) {
        List<BaseAccount> accounts = AccountManager.getAccountsView();
        int y = listTop;
        for (int i = 0; i < accounts.size() && y + LAYOUT_ENTRY_H <= listBottom; i++) {
            BaseAccount account = accounts.get(i);
            int color = account == AccountManager.getCurrentAccount() ? 0x553399FF : 0x55000000;
            drawRect(listLeft, y, listRight, y + LAYOUT_ENTRY_H - 2, color);

            this.fontRenderer.drawStringWithShadow(account.getAccountStorage().getPlayerName(), listLeft + 8, y + 3, 0xFFFFFF);
            this.fontRenderer.drawStringWithShadow(getAccountTypeText(account), listLeft + 8, y + 13, 0xFFFFFF);
            this.fontRenderer.drawStringWithShadow(I18N.TRANSLATOR.translate(account.getAccountStorage().getState()), listLeft + 8, y + 23, 0xFFFFFF);

            if (account.getAccountType() != AccountType.ENV_DEFAULT) {
                drawAction(i, y, "^", 5);
                drawAction(i, y, "x", 15);
                drawAction(i, y, "v", 25);
            }

            y += LAYOUT_ENTRY_H;
        }
    }

    private String getAccountTypeText(BaseAccount account) {
        if (account.getAccountType() == AccountType.AUTHLIB_INJECTOR) {
            String accountName = account.getAccountName();
            if (accountName != null && !accountName.isEmpty()) {
                return I18N.TRANSLATOR.translate("accountsx.account.type.authlib_injector.named", accountName);
            }
        }
        return I18N.TRANSLATOR.translate(account.getAccountType());
    }

    private void drawAction(int index, int rowTop, String text, int offset) {
        if ("^".equals(text) && index <= 1) {
            return;
        }
        if ("v".equals(text) && index >= AccountManager.getAccountsView().size() - 1) {
            return;
        }
        this.fontRenderer.drawStringWithShadow(text, listRight - 16, rowTop + offset, 0xFFFFFF);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return;
        }

        final int index = (mouseY - listTop) / LAYOUT_ENTRY_H;
        List<BaseAccount> accounts = AccountManager.getAccountsView();
        if (index < 0 || index >= accounts.size()) {
            return;
        }

        BaseAccount account = accounts.get(index);
        int rowTop = listTop + index * LAYOUT_ENTRY_H;
        if (account.getAccountType() != AccountType.ENV_DEFAULT && mouseX >= listRight - 24) {
            if (index > 1 && mouseY >= rowTop + 5 && mouseY <= rowTop + 14) {
                AccountManager.moveAccount(account, index - 1);
                return;
            }
            if (mouseY >= rowTop + 15 && mouseY <= rowTop + 24) {
                AccountManager.dropAccount(account);
                return;
            }
            if (index < accounts.size() - 1 && mouseY >= rowTop + 25 && mouseY <= rowTop + 34) {
                AccountManager.moveAccount(account, index + 1);
                return;
            }
        }

        switchAccount(account);
    }

    private void switchAccount(final BaseAccount account) {
        if (AccountManager.getCurrentAccount() == account) {
            return;
        }

        AccountWorker.submit(new AccountWorker.Task() {
            @Override
            public void run() throws Exception {
                if (AccountManager.getCurrentAccount() == account) {
                    return;
                }

                final AccountSession session = AccountManager.loginAccount(account);
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        AccountManager.switchAccount(account, session);
                    }
                });
            }
        });
    }
}
