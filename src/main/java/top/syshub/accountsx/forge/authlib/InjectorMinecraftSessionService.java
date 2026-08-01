package top.syshub.accountsx.forge.authlib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.accounts.AccountUUID;
import top.syshub.accountsx.core.accounts.model.context.AccountContext;
import top.syshub.accountsx.core.utils.NetworkUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class InjectorMinecraftSessionService implements MinecraftSessionService {
    private final AccountContext context;
    private final Proxy proxy;
    private final String sessionBaseUrl;

    InjectorMinecraftSessionService(AccountContext context, Proxy proxy) {
        this.context = context;
        this.proxy = proxy == null ? Proxy.NO_PROXY : proxy;
        this.sessionBaseUrl = stripTrailingSlash(context.server().sessionURL());
    }

    @Override
    public void joinServer(GameProfile profile, String accessToken, String serverId) throws AuthenticationException {
        JsonObject request = new JsonObject();
        request.addProperty("accessToken", accessToken);
        request.addProperty("selectedProfile", AccountUUID.toMinecraftStyleString(profile.getId()));
        request.addProperty("serverId", serverId);
        requestJson("POST", joinUrl(), request);
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile profile, String serverId, InetAddress address) throws AuthenticationUnavailableException {
        try {
            String url = hasJoinedUrl(profile, serverId, address);
            JsonObject response = requestJson("GET", url, null);
            if (response == null || !response.has("id")) {
                return null;
            }
            return readProfile(response, profile);
        } catch (AuthenticationUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationUnavailableException("Cannot check external Yggdrasil join state.", e);
        }
    }

    @Override
    public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(GameProfile profile, boolean requireSecure) {
        Property textures = firstProperty(profile.getProperties().get("textures"));
        if (textures == null) {
            return Collections.emptyMap();
        }

        if (requireSecure) {
            if (!textures.hasSignature()) {
                throw new InsecureTextureException("Signature is missing from textures payload");
            }
            if (!isValidSignature(textures)) {
                throw new InsecureTextureException("Textures payload signature is invalid for this external Yggdrasil server");
            }
        }

        try {
            String payload = new String(Base64.getDecoder().decode(textures.getValue()), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(payload).getAsJsonObject();
            JsonElement texturesElement = root.get("textures");
            if (!(texturesElement instanceof JsonObject)) {
                return Collections.emptyMap();
            }

            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> result = new LinkedHashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture>();
            JsonObject texturesJson = texturesElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : texturesJson.entrySet()) {
                if (!(entry.getValue() instanceof JsonObject)) {
                    continue;
                }
                MinecraftProfileTexture.Type type;
                try {
                    type = MinecraftProfileTexture.Type.valueOf(entry.getKey());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                JsonObject texture = entry.getValue().getAsJsonObject();
                JsonElement url = texture.get("url");
                if (url == null || !url.isJsonPrimitive()) {
                    continue;
                }
                result.put(type, new MinecraftProfileTexture(url.getAsString(), readMetadata(texture)));
            }
            return result;
        } catch (Exception e) {
            AccountsX.LOGGER.warn("Cannot decode external Yggdrasil textures payload.", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
        if (profile == null || profile.getId() == null) {
            return profile;
        }

        try {
            String unsigned = Boolean.toString(!requireSecure);
            JsonObject response = requestJson(
                    "GET",
                    profileUrl(AccountUUID.toMinecraftStyleString(profile.getId()), unsigned),
                    null
            );
            if (response == null || !response.has("id")) {
                return profile;
            }
            return readProfile(response, profile);
        } catch (Exception e) {
            AccountsX.LOGGER.warn("Cannot fetch external Yggdrasil profile properties for {}.", profile, e);
            return profile;
        }
    }

    private GameProfile readProfile(JsonObject response, GameProfile fallbackProfile) {
        String id = stringOrNull(response.get("id"));
        String name = stringOrNull(response.get("name"));
        GameProfile result = new GameProfile(
                id == null ? fallbackProfile.getId() : AccountUUID.parse(id),
                name == null ? fallbackProfile.getName() : name
        );
        copyProperties(response, result);
        fallbackProfile.getProperties().putAll(result.getProperties());
        return result;
    }

    private void copyProperties(JsonObject response, GameProfile profile) {
        JsonElement properties = response.get("properties");
        if (!(properties instanceof JsonArray)) {
            return;
        }

        for (JsonElement element : properties.getAsJsonArray()) {
            if (!(element instanceof JsonObject)) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            String name = stringOrNull(property.get("name"));
            String value = stringOrNull(property.get("value"));
            String signature = stringOrNull(property.get("signature"));
            if (name == null || value == null) {
                continue;
            }
            profile.getProperties().put(name, signature == null ? new Property(name, value) : new Property(name, value, signature));
        }
    }

    private boolean isValidSignature(Property property) {
        for (PublicKey key : context.security().profilePropertyKeys()) {
            if (property.isSignatureValid(key)) {
                return true;
            }
        }
        return false;
    }

    private static Property firstProperty(Collection<Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        return properties.iterator().next();
    }

    private static Map<String, String> readMetadata(JsonObject texture) {
        JsonElement metadata = texture.get("metadata");
        if (!(metadata instanceof JsonObject)) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : metadata.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private JsonObject requestJson(String method, String url, JsonObject body) throws AuthenticationException {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = (HttpURLConnection) (proxy == Proxy.NO_PROXY ? target.openConnection() : target.openConnection(proxy));
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");

            if (body != null) {
                byte[] payload = NetworkUtils.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }

            int status = connection.getResponseCode();
            String response = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status / 100 != 2) {
                throw authenticationException(status, connection.getResponseMessage(), response);
            }
            if (response == null || response.trim().isEmpty()) {
                return null;
            }
            JsonElement parsed = new JsonParser().parse(response);
            return parsed instanceof JsonObject ? parsed.getAsJsonObject() : null;
        } catch (AuthenticationException e) {
            throw e;
        } catch (IOException e) {
            throw new AuthenticationUnavailableException("Cannot reach external Yggdrasil session server: " + url, e);
        } catch (RuntimeException e) {
            throw new AuthenticationException("Invalid external Yggdrasil session response from " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static AuthenticationException authenticationException(int status, String reason, String response) {
        String message = null;
        if (response != null && !response.trim().isEmpty()) {
            try {
                JsonObject json = new JsonParser().parse(response).getAsJsonObject();
                message = stringOrNull(json.get("errorMessage"));
                if (message == null) {
                    message = stringOrNull(json.get("error"));
                }
            } catch (RuntimeException ignored) {
                message = response;
            }
        }
        if (message == null || message.isEmpty()) {
            message = reason == null ? "HTTP " + status : "HTTP " + status + ": " + reason;
        }
        return new AuthenticationException(message);
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        input.close();
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private String joinUrl() {
        return sessionBaseUrl + "/session/minecraft/join";
    }

    private String profileUrl(String uuid, String unsigned) {
        return sessionBaseUrl + "/session/minecraft/profile/" + encode(uuid) + "?unsigned=" + encode(unsigned);
    }

    private String hasJoinedUrl(GameProfile profile, String serverId, InetAddress address) {
        StringBuilder url = new StringBuilder(sessionBaseUrl)
                .append("/session/minecraft/hasJoined?username=").append(encode(profile.getName()))
                .append("&serverId=").append(encode(serverId));
        if (address != null) {
            url.append("&ip=").append(encode(address.getHostAddress()));
        }
        return url.toString();
    }

    private static String stringOrNull(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static String stripTrailingSlash(String url) {
        while (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
