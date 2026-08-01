package top.syshub.accountsx.core.manager.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.accounts.BaseAccount;
import top.syshub.accountsx.core.accounts.model.AccountType;
import top.syshub.accountsx.core.manager.AccountManager;
import top.syshub.accountsx.core.utils.NetworkUtils;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ConfigHandle {
    private ConfigHandle() {}

    private static String id;

    private static final String CONFIG_LOCATION = "accountsx/accounts.json";

    private static Path getConfigFile() {
        return Minecraft.getMinecraft().gameDir.toPath().resolve("config").resolve(CONFIG_LOCATION);
    }

    private static void writeString(Path file, String text) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    private static final class Config {
        public static final int CURRENT_VERSION = ConfigVersion.VALUES[ConfigVersion.VALUES.length - 1].getVersion();

        private final int version;

        private final String id;

        private final List<BaseAccount> accounts;

        private Config(List<BaseAccount> accounts) {
            this.version = CURRENT_VERSION;
            if (ConfigHandle.id == null)
                ConfigHandle.id = UUID.randomUUID().toString();

            id = ConfigHandle.id;
            this.accounts = accounts;
        }
    }

    public static List<? extends BaseAccount> load() {
        Path configFile = getConfigFile();

        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                writeString(configFile, NetworkUtils.GSON.toJson(new Config(Collections.<BaseAccount>emptyList())));
                return Collections.emptyList();
            }

            if (!Files.isRegularFile(configFile)) {
                Files.delete(configFile);
                writeString(configFile, NetworkUtils.GSON.toJson(new Config(Collections.<BaseAccount>emptyList())));
                return Collections.emptyList();
            }

            JsonElement data;
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                data = NetworkUtils.GSON.fromJson(reader, JsonElement.class);
            }

            if (data instanceof JsonObject) {
                JsonObject jo = (JsonObject) data;
                if (jo.get("version") instanceof JsonPrimitive && ((JsonPrimitive) jo.get("version")).isNumber()) {
                    JsonPrimitive versionJP = (JsonPrimitive) jo.get("version");
                    int configVersion = versionJP.getAsNumber().intValue();

                    for (ConfigVersion value : ConfigVersion.VALUES) {
                        if (configVersion < value.getVersion()) {
                            value.upgrade(jo);
                        }
                    }

                    id = jo.get("id").getAsString();
                    try {
                        UUID.fromString(id);
                    } catch (Exception e) {
                        id = UUID.randomUUID().toString();
                    }
                    if (jo.get("accounts") instanceof JsonArray) {
                        return NetworkUtils.GSON.fromJson(
                                jo.get("accounts"),
                                new TypeToken<List<BaseAccount>>() {}.getType()
                        );
                    }
                    return getLegacyAccounts();
                }
            }

            throw new IllegalStateException("Illegal config.");
        } catch (Throwable t) {
            AccountsX.LOGGER.warn("Cannot load the config file.", t);
            return Collections.emptyList();
        }
    }

    public static void write() throws IOException {
        Path configFile = getConfigFile();

        List<BaseAccount> accounts = new ArrayList<>();

        for (BaseAccount account : AccountManager.getAccountsView()) {
            if (account.getAccountType() != AccountType.ENV_DEFAULT) {
                accounts.add(account);
            }
        }

        Files.createDirectories(configFile.getParent());
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            NetworkUtils.GSON.toJson(new Config(accounts), writer);
        }
    }

    private static List<? extends BaseAccount> getLegacyAccounts() {
        String userHome = System.getProperty("user.home");
        Path accountsFile = new java.io.File(new java.io.File(userHome, ".accountsx"), id + ".json").toPath();

        try {
            if (!Files.exists(accountsFile) || !Files.isRegularFile(accountsFile))
                return Collections.emptyList();

            try (Reader reader = Files.newBufferedReader(accountsFile, StandardCharsets.UTF_8)) {
                return NetworkUtils.GSON.fromJson(
                        reader,
                        new TypeToken<List<? extends BaseAccount>>() {}.getType()
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load accounts file", e);
        }
    }

    static void writeAccounts(String id, String accountString) {
        String userHome = System.getProperty("user.home");
        Path accountsFile = new java.io.File(new java.io.File(userHome, ".accountsx"), id + ".json").toPath();

        try {
            Files.createDirectories(accountsFile.getParent());
            writeString(accountsFile, accountString);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write accounts file", e);
        }
    }
}
