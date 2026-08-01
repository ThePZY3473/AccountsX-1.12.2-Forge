package top.syshub.accountsx.forge.gui;

import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.accounts.AccountProvider;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.manager.AccountManager;
import top.syshub.accountsx.core.manager.AccountWorker;
import top.syshub.accountsx.core.ui.Memory;
import top.syshub.accountsx.core.ui.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UIScreenImpl implements UIScreen {
    public static GuiScreen create(AccountScreen parent, AccountProvider<?> provider) {
        UIScreenImpl screen = new UIScreenImpl();
        provider.configure(screen);
        return screen.bind(parent, provider);
    }

    private static final class ValuedWidget {
        private final String guid;
        private final String description;
        private GuiTextField widget;

        private ValuedWidget(String guid, String description) {
            this.guid = guid;
            this.description = description;
        }
    }

    private boolean readonly;
    private String title;
    private final Map<String, ValuedWidget> inputs = new LinkedHashMap<String, ValuedWidget>();

    @Override
    public void setTitle(String description) {
        if (readonly) {
            throw new IllegalStateException("UIScreen has been frozen.");
        }
        this.title = description;
    }

    @Override
    public void putTextInput(String guid, String description) {
        if (readonly) {
            throw new IllegalStateException("UIScreen has been frozen.");
        }
        this.inputs.put(guid, new ValuedWidget(guid, description));
    }

    @Override
    public String getTextInput(String guid) {
        if (!readonly) {
            throw new IllegalStateException("UIScreen hasn't been frozen.");
        }
        return this.inputs.get(guid).widget.getText();
    }

    private GuiScreen bind(AccountScreen parent, AccountProvider<?> provider) {
        readonly = true;
        return new LoginScreen(parent, provider);
    }

    private final class LoginScreen extends GuiScreen {
        private static final int LOGIN_BUTTON_ID = 1;
        private static final int CLOSE_BUTTON_ID = 2;

        private final AccountScreen parent;
        private final AccountProvider<?> provider;
        private final List<GuiTextField> fields = new ArrayList<GuiTextField>();

        private LoginScreen(AccountScreen parent, AccountProvider<?> provider) {
            this.parent = parent;
            this.provider = provider;
        }

        @Override
        public void initGui() {
            this.buttonList.clear();
            this.fields.clear();

            int widgetsTop = this.height / 2 - (UIScreenImpl.this.inputs.size() + 1) * 25 / 2;
            int widgetsLeft = this.width / 2 - 50;

            int id = 10;
            for (ValuedWidget widget : UIScreenImpl.this.inputs.values()) {
                GuiTextField field = new GuiTextField(id++, this.fontRenderer, widgetsLeft, widgetsTop, 200, 20);
                field.setMaxStringLength(512);
                widget.widget = field;
                this.fields.add(field);
                widgetsTop += 25;
            }

            boolean noInputs = UIScreenImpl.this.inputs.isEmpty();
            this.buttonList.add(new GuiButton(LOGIN_BUTTON_ID, widgetsLeft, widgetsTop, noInputs ? 100 : 95, 20, I18N.TRANSLATOR.translate("accountsx.account.general.login")));
            if (noInputs) {
                this.buttonList.add(new GuiButton(CLOSE_BUTTON_ID, widgetsLeft, widgetsTop + 25, 100, 20, I18N.TRANSLATOR.translate("accountsx.general.action.close")));
            } else {
                this.buttonList.add(new GuiButton(CLOSE_BUTTON_ID, widgetsLeft + 105, widgetsTop, 95, 20, I18N.TRANSLATOR.translate("accountsx.general.action.close")));
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button.id == CLOSE_BUTTON_ID) {
                close();
            } else if (button.id == LOGIN_BUTTON_ID) {
                login();
            }
        }

        private void close() {
            this.mc.displayGuiScreen(parent);
        }

        private void login() {
            final Memory memory = new DefaultMemory(this);

            int state;
            try {
                state = this.provider.validate(UIScreenImpl.this, memory);
            } catch (IllegalArgumentException e) {
                AccountsX.LOGGER.warn("Invalid account argument.", e);
                return;
            }

            if (state == AccountProvider.STATE_IMMEDIATE_CLOSE) {
                close();
            } else if (state != AccountProvider.STATE_HANDLE) {
                throw new IllegalArgumentException("Unknown state: " + state);
            }

            AccountWorker.submit(new AccountWorker.Task() {
                @Override
                public void run() throws Exception {
                    final BaseAccount account = provider.login(memory);
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (Minecraft.getMinecraft().currentScreen == LoginScreen.this) {
                                close();
                            }
                            AccountManager.addAccount(account);
                            parent.syncAccounts();
                        }
                    });
                }
            });
        }

        @Override
        public void updateScreen() {
            for (GuiTextField field : fields) {
                field.updateCursorCounter();
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            super.keyTyped(typedChar, keyCode);
            for (GuiTextField field : fields) {
                field.textboxKeyTyped(typedChar, keyCode);
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            for (GuiTextField field : fields) {
                field.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();

            int textTop = this.height / 2 - (UIScreenImpl.this.inputs.size() + 1) * 25 / 2 + 5;
            int textLeft = this.width / 2 - 170;
            this.drawCenteredString(this.fontRenderer, I18N.TRANSLATOR.translate(UIScreenImpl.this.title), this.width / 2, textTop - 40, 0xFFFFFF);

            for (ValuedWidget widget : UIScreenImpl.this.inputs.values()) {
                this.fontRenderer.drawStringWithShadow(I18N.TRANSLATOR.translate(widget.description), textLeft, textTop, 0xFFFFFF);
                widget.widget.drawTextBox();
                textTop += 25;
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }
}
