package top.syshub.accountsx.core.manager.config;

import com.google.gson.*;
import top.syshub.accountsx.core.utils.NetworkUtils;

import java.util.UUID;

public enum ConfigVersion {
    BASE(0) {
        @Override
        protected void upgrade(JsonObject config) {
            throw new IllegalStateException("There's no more legacy version.");
        }
    }, INJECTOR_SAFETY(1) {
        @Override
        protected void upgrade(JsonObject config) {
            if (config.get("accounts") instanceof JsonArray) {
                JsonArray accounts = (JsonArray) config.get("accounts");
                for (int i = accounts.size() - 1; i >= 0; i--) {
                    JsonElement account = accounts.get(i);
                    if (account instanceof JsonObject && ((JsonObject) account).get("type") instanceof JsonPrimitive && ((JsonPrimitive) ((JsonObject) account).get("type")).isString()) {
                        JsonPrimitive jp = (JsonPrimitive) ((JsonObject) account).get("type");
                        if ("INJECTOR".equals(jp.getAsString())) {
                            accounts.remove(i);
                        }
                    }
                }
            }
        }
    }, RENAME_ACCOUNT_TYPE(2) {
        @Override
        protected void upgrade(JsonObject config) {
            if (config.get("accounts") instanceof JsonArray) {
                JsonArray accounts = (JsonArray) config.get("accounts");
                for (JsonElement account : accounts) {
                    if (account instanceof JsonObject && ((JsonObject) account).get("type") instanceof JsonPrimitive && ((JsonPrimitive) ((JsonObject) account).get("type")).isString()) {
                        JsonObject jo = (JsonObject) account;
                        JsonPrimitive jp = (JsonPrimitive) jo.get("type");
                        String type = jp.getAsString();
                        if ("OFFLINE".equals(type)) {
                            jo.addProperty("type", "offline");
                        } else if ("MICROSOFT".equals(type)) {
                            jo.addProperty("type", "microsoft");
                        } else if ("INJECTOR".equals(type)) {
                            jo.addProperty("type", "injector.authlib-injector");
                        } else {
                            throw new IllegalStateException("Unexpected account type: " + type);
                        }
                    }
                }
            }
        }
    }, SECURITY_STORAGE(3) {
        @Override
        protected void upgrade(JsonObject config) {
            String id = UUID.randomUUID().toString();
            config.addProperty("id", id);

            if (config.get("accounts") instanceof JsonArray) {
                JsonArray accounts = (JsonArray) config.get("accounts");
                ConfigHandle.writeAccounts(id, NetworkUtils.GSON.toJson(accounts));
            }
        }
    }, INLINE_ACCOUNTS(4) {
        @Override
        protected void upgrade(JsonObject config) {
        }
    };

    public static final ConfigVersion[] VALUES = values();

    private final int version;

    ConfigVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    protected abstract void upgrade(JsonObject config);
}
