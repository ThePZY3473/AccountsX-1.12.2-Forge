package top.syshub.accountsx.forge.gui;

import top.syshub.accountsx.core.ui.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.IllegalFormatException;
import java.util.Map;

public final class I18N {
    private I18N() {
    }

    public static final Translator<String> TRANSLATOR = new Translator<String>(new Translator.Handle<String>() {
        @Override
        public String translate(String key, String... args) {
            if (I18n.hasKey(key)) {
                return I18n.format(key, (Object[]) args);
            }
            return fallback(key, args);
        }
    });

    private static volatile String loadedLanguage;
    private static volatile Map<String, String> fallbackTranslations = new LinkedHashMap<String, String>();

    private static String fallback(String key, String... args) {
        String language = "en_us";
        try {
            if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().gameSettings != null && Minecraft.getMinecraft().gameSettings.language != null) {
                language = Minecraft.getMinecraft().gameSettings.language;
            }
        } catch (Throwable ignored) {
        }

        Map<String, String> translations = fallbackTranslations;
        if (!language.equals(loadedLanguage)) {
            synchronized (I18N.class) {
                if (!language.equals(loadedLanguage)) {
                    translations = loadFallback(language);
                    fallbackTranslations = translations;
                    loadedLanguage = language;
                } else {
                    translations = fallbackTranslations;
                }
            }
        }

        String value = translations.get(key);
        if (value == null) {
            return key;
        }
        try {
            return String.format(value, (Object[]) args);
        } catch (IllegalFormatException e) {
            return value;
        }
    }

    private static Map<String, String> loadFallback(String language) {
        Map<String, String> result = builtin("en_us");
        loadFallbackFile(result, "en_us");
        if (!"en_us".equals(language)) {
            result.putAll(builtin(language));
            loadFallbackFile(result, language);
        }
        return result;
    }

    private static Map<String, String> builtin(String language) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if ("zh_cn".equals(language)) {
            result.put("accountsx.account.fail.player_no_longer_existed", "未能查找到匹配的玩家！");
            result.put("accountsx.account.fail.title", "账户操作失败");
            result.put("accountsx.account.fail.unknown", "未知错误。请查看日志，或询问社区帮助");
            result.put("accountsx.account.general.account_list", "账户列表");
            result.put("accountsx.account.general.add_account", "添加账户");
            result.put("accountsx.account.general.external", "请在弹出的外部应用程序中按照提示操作");
            result.put("accountsx.account.general.login", "登录");
            result.put("accountsx.account.general.operating", "正在操作 ...");
            result.put("accountsx.account.oauth2.code.desc", "代码（已复制到剪贴板）：%s");
            result.put("accountsx.account.oauth2.code.generating", "正在生成 OAuth2 代码");
            result.put("accountsx.account.oauth2.code.title", "OAuth2 代码生成完毕");
            result.put("accountsx.account.objects.player_name", "玩家 ID");
            result.put("accountsx.account.objects.player_uuid", "玩家 UUID");
            result.put("accountsx.account.objects.server_domain", "认证服务器");
            result.put("accountsx.account.objects.server_id", "服务器 ID");
            result.put("accountsx.account.objects.user_id", "用户 ID（OAuth留空）");
            result.put("accountsx.account.objects.user_name", "用户 ID");
            result.put("accountsx.account.objects.user_password", "用户密码");
            result.put("accountsx.account.state.authorized.name", "正常可用");
            result.put("accountsx.account.state.authorizing.name", "登录中");
            result.put("accountsx.account.state.unauthorized.name", "未登录");
            result.put("accountsx.account.type.authlib_injector.named", "%s | 外置登录");
            result.put("accountsx.account.type.authlib_injector.name", "外置登录账号");
            result.put("accountsx.account.type.authlib_injector.using", "正在使用外置登录账号：%s");
            result.put("accountsx.account.type.env_default.name", "游戏默认账户");
            result.put("accountsx.account.type.env_default.using", "正在使用游戏默认账号：%s");
            result.put("accountsx.account.type.microsoft.name", "微软账号");
            result.put("accountsx.account.type.microsoft.using", "正在使用微软账号：%s");
            result.put("accountsx.account.type.offline.name", "离线账号");
            result.put("accountsx.account.type.offline.using", "正在使用离线账号：%s");
            result.put("accountsx.account.type.united_injector.name", "统一通行证");
            result.put("accountsx.account.type.united_injector.using", "正在使用统一通行证账户：%s");
            result.put("accountsx.general.action.close", "返回");
            result.put("accountsx.general.action.ok", "我明白了");
        } else {
            result.put("accountsx.account.fail.player_no_longer_existed", "Cannot find a matched player!");
            result.put("accountsx.account.fail.title", "Account Operation Failed");
            result.put("accountsx.account.fail.unknown", "Unknown exceptions have occurred. Please check the logs or seek help.");
            result.put("accountsx.account.general.account_list", "Accounts");
            result.put("accountsx.account.general.add_account", "Add Account");
            result.put("accountsx.account.general.external", "Please follow the instructions in the external application that pops up");
            result.put("accountsx.account.general.login", "Login");
            result.put("accountsx.account.general.operating", "Operating...");
            result.put("accountsx.account.oauth2.code.desc", "Code (copied to clipboard): %s");
            result.put("accountsx.account.oauth2.code.generating", "Generating OAuth2 Code");
            result.put("accountsx.account.oauth2.code.title", "OAuth2 Code Generated");
            result.put("accountsx.account.objects.player_name", "Player ID");
            result.put("accountsx.account.objects.player_uuid", "Player UUID");
            result.put("accountsx.account.objects.server_domain", "Yggdrasil API URL");
            result.put("accountsx.account.objects.server_id", "Server ID");
            result.put("accountsx.account.objects.user_id", "User ID (Leave blank to use OAuth)");
            result.put("accountsx.account.objects.user_name", "User ID");
            result.put("accountsx.account.objects.user_password", "User Password");
            result.put("accountsx.account.state.authorized.name", "Available");
            result.put("accountsx.account.state.authorizing.name", "Logging In");
            result.put("accountsx.account.state.unauthorized.name", "Not Logged In");
            result.put("accountsx.account.type.authlib_injector.named", "%s | External Login");
            result.put("accountsx.account.type.authlib_injector.name", "Yggdrasil API Authentication");
            result.put("accountsx.account.type.authlib_injector.using", "Using Yggdrasil API Authentication Account: %s");
            result.put("accountsx.account.type.env_default.name", "Game Default Account");
            result.put("accountsx.account.type.env_default.using", "Using Game Default Account: %s");
            result.put("accountsx.account.type.microsoft.name", "Microsoft Account");
            result.put("accountsx.account.type.microsoft.using", "Using Microsoft Account: %s");
            result.put("accountsx.account.type.offline.name", "Offline Account");
            result.put("accountsx.account.type.offline.using", "Using Offline Account: %s");
            result.put("accountsx.account.type.united_injector.name", "United Pass Phrase Authentication");
            result.put("accountsx.account.type.united_injector.using", "Using United Pass Phrase Authentication Account: %s");
            result.put("accountsx.general.action.close", "Back");
            result.put("accountsx.general.action.ok", "OK");
        }
        return result;
    }

    private static void loadFallbackFile(Map<String, String> result, String language) {
        String path = "/assets/accountsx/lang/" + language + ".lang";
        InputStream input = I18N.class.getResourceAsStream(path);
        if (input == null) {
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    int separator = line.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    result.put(line.substring(0, separator), line.substring(separator + 1));
                }
            } finally {
                reader.close();
            }
        } catch (IOException ignored) {
        }
    }
}
