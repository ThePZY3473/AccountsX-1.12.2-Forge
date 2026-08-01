package top.syshub.accountsx.core.accounts.impl.injector;

import com.google.gson.*;
import top.syshub.accountsx.core.accounts.AccountProvider;
import top.syshub.accountsx.core.accounts.AccountUUID;
import top.syshub.accountsx.core.accounts.model.PlayerNoLongerExistedException;
import top.syshub.accountsx.core.accounts.model.context.*;
import top.syshub.accountsx.core.adapters.Adapters;
import top.syshub.accountsx.core.ui.Memory;
import top.syshub.accountsx.core.ui.UIScreen;
import top.syshub.accountsx.core.utils.AvatarUtils;
import top.syshub.accountsx.core.utils.NetworkUtils;

import java.io.IOException;
import java.net.URI;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public abstract class AbstractInjectorAccountProvider<T extends AbstractInjectorAccount> implements AccountProvider<T> {
    private static final String GUID_SERVER_BASE = "guid:as.login.injector.widgets.server_url";
    private static final String GUID_USER_NAME = "guid:as.login.injector.widgets.user_name";
    private static final String GUID_PASSWORD = "guid:as.login.injector.widgets.user_password";
    private static final String GUID_PLAYER_NAME = "guid:as.login.injector.widgets.player_name";

    private final String serverBaseTranslationKey;

    private final String userBaseTranslationKey;

    private final String accountContextName;

    protected AbstractInjectorAccountProvider(String serverBaseTranslationKey, String userBaseTranslationKey, String accountContextName) {
        this.serverBaseTranslationKey = serverBaseTranslationKey;
        this.accountContextName = accountContextName;
        this.userBaseTranslationKey = userBaseTranslationKey;
    }

    protected void validateServerBaseURL(String server) throws IllegalArgumentException {}

    protected abstract String transformServerBaseURL(String server);

    protected abstract T createAccount(String accessToken, String playerName, UUID playerUUID, String server, String preferredPlayerUUID,
                                       String clientToken, String accountName, String avatar);

    @Override
    public final AccountContext createAccountContext(T account) throws IOException {
        String url = stripTrailingSlash(account.getServer());

        List<PublicKey> publicKeys;
        List<String> skinDomains = new ArrayList<>();

        JsonObject response = NetworkUtils.postRequest(NetworkUtils.buildGet(url));
        if (response.get("signaturePublickey") instanceof JsonPrimitive && ((JsonPrimitive) response.get("signaturePublickey")).isString()) {
            JsonPrimitive jp = (JsonPrimitive) response.get("signaturePublickey");
            try {
                publicKeys = Collections.singletonList(parseSignaturePublicKey(jp.getAsString()));
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IOException("Invalid yggdrasil public key!", e);
            }
        } else {
            throw new IOException("Invalid yggdrasil public key!");
        }

        if (response.get("skinDomains") instanceof JsonArray) {
            JsonArray ja = (JsonArray) response.get("skinDomains");
            for (JsonElement je : ja) {
                if (je instanceof JsonPrimitive && ((JsonPrimitive) je).isString()) {
                    JsonPrimitive domain = (JsonPrimitive) je;
                    skinDomains.add(domain.getAsString());
                } else {
                    throw new IOException("Invalid yggdrasil public key!");
                }
            }
        } else {
            throw new IOException("Invalid yggdrasil public key!");
        }

        return new AccountContext(new AuthServerContext(
                url + "/authserver",
                url + "/api",
                url + "/sessionserver",
                url + "/minecraftservices",
                accountContextName
        ), new AuthSecurityContext(
                publicKeys, publicKeys,
                SkinURLVerifier.ofOperationOR(SkinURLVerifier.ofDomainVerifier(skinDomains, Collections.<String>emptyList()), SkinURLVerifier.MOJANG_DEFAULT)
        ), AuthPolicy.TRY);
    }

    private static PublicKey parseSignaturePublicKey(String pem) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        pem = pem.replace("\n", "").replace("\r", "");

        String header = "-----BEGIN PUBLIC KEY-----", end = "-----END PUBLIC KEY-----";
        if (!pem.startsWith(header) || !pem.endsWith(end)) {
            throw new IOException("Bad key format");
        }

        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(pem.substring(header.length(), pem.length() - end.length()))
        ));
    }

    @Override
    public final void configure(UIScreen screen) {
        screen.setTitle("accountsx.account.general.login");
        screen.putTextInput(GUID_SERVER_BASE, serverBaseTranslationKey);
        screen.putTextInput(GUID_USER_NAME, userBaseTranslationKey);
        screen.putTextInput(GUID_PASSWORD, "accountsx.account.objects.user_password");
        screen.putTextInput(GUID_PLAYER_NAME, "accountsx.account.objects.player_name");
    }

    @Override
    public final int validate(UIScreen screen, Memory memory) throws IllegalArgumentException {
        String serverBase = screen.getTextInput(GUID_SERVER_BASE);
        validateServerBaseURL(serverBase);
        memory.set(GUID_SERVER_BASE, serverBase);
        memory.set(GUID_USER_NAME, screen.getTextInput(GUID_USER_NAME));
        memory.set(GUID_PASSWORD, screen.getTextInput(GUID_PASSWORD));
        memory.set(GUID_PLAYER_NAME, screen.getTextInput(GUID_PLAYER_NAME));

        return STATE_IMMEDIATE_CLOSE;
    }

    @Override
    public final T login(Memory memory) throws IOException {
        if (memory.get(GUID_USER_NAME, String.class).isEmpty()) return loginOAuth(memory.get(GUID_SERVER_BASE, String.class));
        String filledServer = memory.get(GUID_SERVER_BASE, String.class);
        String baseUrl = ensureTrailingSlash(transformServerBaseURL(filledServer));
        String loginUrl = baseUrl + "authserver/authenticate";
        String apiRoot = baseUrl;
        String profileUrl = apiRoot + "sessionserver/session/minecraft/profile/";
        String refreshUrl = apiRoot + "authserver/refresh";
        String clientToken = AccountUUID.toMinecraftStyleString(UUID.randomUUID());

        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);

        JsonObject root = new JsonObject();
        root.add("agent", agent);
        root.addProperty("username", memory.get(GUID_USER_NAME, String.class));
        root.addProperty("password", memory.get(GUID_PASSWORD, String.class));
        root.addProperty("clientToken", clientToken);
        root.addProperty("requestUser", true);

        JsonObject json = NetworkUtils.postRequest(loginUrl, root);
        if (json.has("error")) {
            throw new IOException("Cannot auth this injector: " + json.get("errorMessage").getAsString());
        }

        String accessToken = json.get("accessToken").getAsString();
        clientToken = json.has("clientToken") ? json.get("clientToken").getAsString() : clientToken;

        String playerName = memory.get(GUID_PLAYER_NAME, String.class);
        List<Profile> profiles = readProfiles(json);
        Profile selectedProfile = selectProfile(profiles, playerName);
        if (json.get("selectedProfile") == null) {
            json = refreshSession(refreshUrl, accessToken, clientToken, selectedProfile);
            accessToken = json.get("accessToken").getAsString();
            clientToken = json.has("clientToken") ? json.get("clientToken").getAsString() : clientToken;
            profiles = readProfiles(json);
        }
        if (profiles.size() == 1) {
            Profile profile = ensureProfileName(profileUrl, profiles.get(0));

            if (!playerName.isEmpty()) {
                if (!playerName.equals(profile.playerName)) {
                    throw new IOException("Player not found.");
                }
            }

            return createAccount(
                    accessToken, profile.playerName,
                    AccountUUID.parse(profile.playerUUID),
                    apiRoot,
                    profile.playerUUID,
                    clientToken,
                    getAccountName(apiRoot),
                    AvatarUtils.getAvatar(profileUrl, profile.playerUUID)
            );
        } else {
            Profile profile = ensureProfileName(profileUrl, selectProfile(profiles, playerName));
            return createAccount(
                    accessToken,
                    profile.playerName,
                    AccountUUID.parse(profile.playerUUID),
                    apiRoot,
                    profile.playerUUID,
                    clientToken,
                    getAccountName(apiRoot),
                    AvatarUtils.getAvatar(profileUrl, profile.playerUUID)
            );
        }
    }

    @Override
    public final void refresh(T account) throws IOException {
        String loginToken = account.getLoginToken();
        if ((loginToken == null || loginToken.isEmpty()) && account.getAccountStorage() != null) {
            loginToken = account.getAccountStorage().getAccessToken();
        }
        if (loginToken == null || loginToken.isEmpty()) {
            throw new IOException("Cannot refresh this injector account because the saved access token is missing.");
        }

        if (loginToken.startsWith("OAuth ")) {
            refreshOAuth(account);
            return;
        }
        String baseUrl = account.getServer();
        String apiRoot = ensureTrailingSlash(baseUrl);
        String refreshUrl = apiRoot + "authserver/refresh";
        String profileUrl = apiRoot + "sessionserver/session/minecraft/profile/";
        String clientToken = account.getClientToken();
        if (clientToken == null || clientToken.isEmpty()) {
            clientToken = AccountUUID.toMinecraftStyleString(UUID.randomUUID());
        }

        JsonObject root = new JsonObject();
        root.addProperty("accessToken", loginToken);
        root.addProperty("clientToken", clientToken);
        root.addProperty("requestUser", true);
        String preferredPlayerUUID = account.getPreferredPlayerUUID();
        if ((preferredPlayerUUID == null || preferredPlayerUUID.isEmpty()) && account.getAccountStorage() != null && account.getAccountStorage().getPlayerUUID() != null) {
            preferredPlayerUUID = AccountUUID.toMinecraftStyleString(account.getAccountStorage().getPlayerUUID());
        }
        if (preferredPlayerUUID != null && !preferredPlayerUUID.isEmpty()) {
            JsonObject selectedProfile = new JsonObject();
            selectedProfile.addProperty("id", preferredPlayerUUID);
            root.add("selectedProfile", selectedProfile);
        }

        JsonObject json = NetworkUtils.postRequest(refreshUrl, root);
        if (json.has("error")) {
            throw new IOException("Cannot auth this injector: " + json.get("errorMessage").getAsString());
        }

        String accessToken = json.get("accessToken").getAsString();
        clientToken = json.has("clientToken") ? json.get("clientToken").getAsString() : clientToken;

        List<Profile> profiles = readProfiles(json);
        if (profiles.size() == 1) {
            Profile profile = ensureProfileName(profileUrl, profiles.get(0));
            account.setLoginProfile(accessToken, profile.playerUUID, clientToken);
            account.setProfile(accessToken, profile.playerName, AccountUUID.parse(profile.playerUUID));
        } else {
            for (Profile profile : profiles) {
                if (profile.playerUUID.equals(preferredPlayerUUID)) {
                    profile = ensureProfileName(profileUrl, profile);
                    account.setLoginProfile(accessToken, profile.playerUUID, clientToken);
                    account.setProfile(accessToken, profile.playerName, AccountUUID.parse(profile.playerUUID));
                    account.setAvatar(AvatarUtils.getAvatar(profileUrl, profile.playerUUID));
                    return;
                }
            }

            throw new PlayerNoLongerExistedException("Cannot find player which match " + preferredPlayerUUID);
        }
    }

    private static Profile ensureProfileName(String profileUrl, Profile profile) throws IOException {
        if (profile.playerName != null && !profile.playerName.isEmpty()) {
            return profile;
        }
        try {
            JsonObject profileJson = NetworkUtils.postRequest(NetworkUtils.buildGet(profileUrl + profile.playerUUID));
            JsonElement name = profileJson.get("name");
            if (name instanceof JsonPrimitive && ((JsonPrimitive) name).isString() && !name.getAsString().isEmpty()) {
                return new Profile(name.getAsString(), profile.playerUUID);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception ignored) {
        }
        throw new IOException("Cannot resolve player name for injector profile " + profile.playerUUID);
    }

    private static Profile selectProfile(List<Profile> profiles, String playerName) throws PlayerNoLongerExistedException {
        if (profiles.size() == 1) {
            return profiles.get(0);
        }
        for (Profile profile : profiles) {
            if (playerName.equals(profile.playerName)) {
                return profile;
            }
        }
        throw new PlayerNoLongerExistedException("Cannot find player which match " + playerName);
    }

    private static JsonObject refreshSession(String refreshUrl, String accessToken, String clientToken, Profile selectedProfile) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("accessToken", accessToken);
        root.addProperty("clientToken", clientToken);
        root.addProperty("requestUser", true);
        JsonObject selected = new JsonObject();
        selected.addProperty("id", selectedProfile.playerUUID);
        selected.addProperty("name", selectedProfile.playerName);
        root.add("selectedProfile", selected);
        JsonObject json = NetworkUtils.postRequest(refreshUrl, root);
        if (json.has("error")) {
            throw new IOException("Cannot select this injector profile: " + json.get("errorMessage").getAsString());
        }
        return json;
    }

    private static String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static String stripTrailingSlash(String url) {
        while (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String getAccountName(String baseUrl) {
        try {
            JsonObject ygg = NetworkUtils.postRequest(NetworkUtils.buildGet(baseUrl));
            return ygg.get("meta").getAsJsonObject().get("serverName").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class Profile {
        private final String playerName;
        private final String playerUUID;

        private Profile(String playerName, String playerUUID) {
            this.playerName = playerName;
            this.playerUUID = playerUUID;
        }
    }

    private static List<Profile> readProfiles(JsonObject json) {
        List<Profile> available = readAvailableProfiles(json);
        JsonElement selectedProfile = json.get("selectedProfile");
        if (selectedProfile != null) {
            JsonObject jo = selectedProfile.getAsJsonObject();
            String playerName = getString(jo, "name");
            String playerUUID = getString(jo, "id");
            if ((playerName == null || playerName.isEmpty()) && playerUUID != null) {
                for (Profile profile : available) {
                    if (playerUUID.equals(profile.playerUUID)) {
                        playerName = profile.playerName;
                        break;
                    }
                }
            }

            return Collections.singletonList(new Profile(playerName, playerUUID));
        }
        return available;
    }

    private static List<Profile> readAvailableProfiles(JsonObject json) {
        JsonElement availableProfilesElement = json.get("availableProfiles");
        if (!(availableProfilesElement instanceof JsonArray)) {
            return Collections.emptyList();
        }
        JsonArray availableProfiles = availableProfilesElement.getAsJsonArray();
        List<Profile> results = new ArrayList<>(availableProfiles.size());

        for (JsonElement availableProfile : availableProfiles) {
            JsonObject jo = availableProfile.getAsJsonObject();
            String playerName = getString(jo, "name");
            String playerUUID = getString(jo, "id");
            results.add(new Profile(playerName, playerUUID));
        }

        return results;
    }

    private static String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element instanceof JsonPrimitive && ((JsonPrimitive) element).isString()) {
            return element.getAsString();
        }
        return null;
    }

    private T loginOAuth(String server) throws IOException {
        String yggUrl = transformServerBaseURL(server);
        String profileUrl = yggUrl + "/sessionserver/session/minecraft/profile/";

        String openidConfigurationUrl;
        JsonObject ygg = NetworkUtils.postRequest(NetworkUtils.buildGet(yggUrl));
        if (ygg.get("meta") instanceof JsonObject) {
            JsonObject meta = (JsonObject) ygg.get("meta");
            if (meta.get("feature.openid_configuration_url") instanceof JsonPrimitive &&
                    ((JsonPrimitive) meta.get("feature.openid_configuration_url")).isString()) {
                openidConfigurationUrl = meta.get("feature.openid_configuration_url").getAsString();
            } else {
                throw new IOException("Invalid openid configuration url!");
            }
        } else {
            throw new IOException("Invalid openid configuration url!");
        }

        JsonObject config = NetworkUtils.postRequest(NetworkUtils.buildGet(openidConfigurationUrl));
        String deviceAuthorizationEndpoint = config.get("device_authorization_endpoint").getAsString();
        String tokenEndpoint = config.get("token_endpoint").getAsString();
        String clientId;

        String host = URI.create(yggUrl).getHost();
        if (OAuthConstants.list.containsKey(host)) clientId = OAuthConstants.list.get(host);
        else if (config.get("shared_client_id") instanceof JsonPrimitive &&
                ((JsonPrimitive) config.get("shared_client_id")).isString()) clientId = config.get("shared_client_id").getAsString();
        else throw new IOException("Invalid client id!");

        Adapters.getMinecraftAdapter().showToast("accountsx.account.oauth2.code.generating", null);

        Map<String, String> form1 = form(
                "client_id", clientId,
                "scope", "openid offline_access Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join"
        );
        JsonObject device = NetworkUtils.postRequest(deviceAuthorizationEndpoint, form1);
        String deviceCode = device.get("device_code").getAsString();
        String userCode = device.get("user_code").getAsString();
        int interval;
        if (device.get("interval") instanceof JsonPrimitive && ((JsonPrimitive) device.get("interval")).isNumber()) {
            interval = device.get("interval").getAsInt();
        } else {
            interval = 5;
        }
        int expires;
        if (device.get("expires_in") instanceof JsonPrimitive && ((JsonPrimitive) device.get("expires_in")).isNumber()) {
            expires = device.get("expires_in").getAsInt();
        } else {
            expires = 300;
        }
        if (device.get("verification_uri_complete") instanceof JsonPrimitive && ((JsonPrimitive) device.get("verification_uri_complete")).isString()) {
            String verificationUriComplete = device.get("verification_uri_complete").getAsString();
            Adapters.getMinecraftAdapter().openBrowser(verificationUriComplete);
            Adapters.getMinecraftAdapter().copyText(verificationUriComplete);
        } else {
            Adapters.getMinecraftAdapter().copyText(userCode);
            device.get("verification_uri").getAsString();
        }
        Adapters.getMinecraftAdapter().showToast("accountsx.account.oauth2.code.title", "accountsx.account.oauth2.code.desc", userCode);

        String accessToken = null, refreshToken = null, idToken = null;
        for (int i = 0; i < expires; i += interval) {
            try {
                Thread.sleep(Math.max(interval, 1) * 1000L);
            } catch (InterruptedException e) {
                throw new IOException("Interrupted.", e);
            }

            Map<String, String> form2 = form(
                    "client_id", clientId,
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code", deviceCode
            );
            JsonObject token = NetworkUtils.postRequest(tokenEndpoint, form2, true);

            JsonElement err = token.get("error");
            if (err == null) {
                accessToken = token.get("access_token").getAsString();
                refreshToken = token.get("refresh_token").getAsString();
                idToken = token.get("id_token").getAsString();
                break;
            }

            String error = err.getAsString();
            if (error.equals("authorization_pending")) continue;
            if (error.equals("expired_token")) throw new IOException("No character detected.");
            throw new IOException("Unknown error: " + error);
        }

        if (accessToken == null || refreshToken == null || idToken == null) throw new IOException("Invalid token.");

        String[] parts = idToken.split("\\.");
        if (parts.length < 2) throw new IOException("Invalid id token.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(parts[1]));
        JsonObject userinfo = JsonParser.parseString(payload).getAsJsonObject();
        Profile profile = readProfiles(userinfo).get(0);

        JsonObject OAuth = new JsonObject();
        OAuth.addProperty("token_endpoint", tokenEndpoint);
        OAuth.addProperty("refresh_token", refreshToken);
        OAuth.addProperty("client_id", clientId);

        T account = createAccount(
                accessToken,
                profile.playerName,
                AccountUUID.parse(profile.playerUUID),
                yggUrl,
                profile.playerUUID,
                null,
                getAccountName(yggUrl),
                AvatarUtils.getAvatar(profileUrl, profile.playerUUID)
        );
        account.setLoginProfile("OAuth " + OAuth, profile.playerUUID);
        return account;
    }

    private void refreshOAuth(T account) throws IOException {
        String OAuthStr = account.getLoginToken().substring(6);
        JsonObject OAuth = JsonParser.parseString(OAuthStr).getAsJsonObject();

        String tokenEndpoint = OAuth.get("token_endpoint").getAsString();
        String refreshToken = OAuth.get("refresh_token").getAsString();
        String clientId = OAuth.get("client_id").getAsString();

        Map<String, String> form = form(
                "client_id", clientId,
                "grant_type", "refresh_token",
                "refresh_token", refreshToken
        );
        JsonObject token = NetworkUtils.postRequest(tokenEndpoint, form);

        JsonElement err = token.get("error");
        if (err !=null) throw new IOException("Unknown error: " + err.getAsString());
        String accessToken = token.get("access_token").getAsString();
        refreshToken = token.get("refresh_token").getAsString();
        OAuth.addProperty("refresh_token", refreshToken);
        String idToken = token.get("id_token").getAsString();

        String[] parts = idToken.split("\\.");
        if (parts.length < 2) throw new IOException("Invalid id token.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(parts[1]));
        JsonObject userinfo = JsonParser.parseString(payload).getAsJsonObject();
        Profile profile = readProfiles(userinfo).get(0);
        String profileUrl = account.getServer() + "/sessionserver/session/minecraft/profile/";

        account.setProfile(accessToken, profile.playerName, AccountUUID.parse(profile.playerUUID));
        account.setLoginProfile("OAuth " + OAuth, profile.playerUUID);
        account.setAvatar(AvatarUtils.getAvatar(profileUrl, profile.playerUUID));
    }

    private static Map<String, String> form(String... values) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }
}
