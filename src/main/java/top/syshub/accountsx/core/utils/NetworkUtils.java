package top.syshub.accountsx.core.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.syshub.accountsx.core.accounts.AccountUUID;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class NetworkUtils {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new AccountUUID.UUIDTypeAdapter())
            .setPrettyPrinting()
            .create();

    private static final HttpClientBuilder BUILDER = HttpClientBuilder.create().setRedirectStrategy(new DefaultRedirectStrategy());

    public static HttpUriRequest buildGet(String url) {
        return RequestBuilder.get()
                .setUri(url)
                .build();
    }

    public static HttpUriRequest buildGet(String url, Map<String, String> headers) {
        RequestBuilder builder = RequestBuilder.get().setUri(url);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            builder.addHeader(e.getKey(), e.getValue());
        }
        return builder.build();
    }

    public static Map<String, List<String>> headRequest(String url) throws IOException {
        URI uri = URI.create(url);
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        try (CloseableHttpClient httpClient = BUILDER.build()) {
            HttpResponse response = httpClient.execute(request);
            Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
            for (Header header : response.getAllHeaders()) {
                List<String> values = result.get(header.getName());
                if (values == null) {
                    values = new ArrayList<String>();
                    result.put(header.getName(), values);
                }
                values.add(header.getValue());
            }
            return result;
        }
    }

    public static List<String> getHeaderIgnoreCase(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String resolveLocation(String base, String loc) throws IOException {
        try {
            URI baseUri = URI.create(base);
            URI locUri = URI.create(loc);
            URI next = locUri.isAbsolute() ? locUri : baseUri.resolve(locUri);
            return next.toString();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid redirect location", e);
        }
    }

    public static JsonObject postRequest(HttpUriRequest request) throws IOException {
        return postRequest(request, false);
    }

    public static JsonObject postRequest(HttpUriRequest request, boolean ignoreHttpStatus) throws IOException {
        try (CloseableHttpClient httpClient = BUILDER.build()) {
            try (Reader reader = NetworkUtils.readResponse(httpClient.execute(request), ignoreHttpStatus)) {
                return NetworkUtils.GSON.fromJson(reader, JsonObject.class);
            }
        }
    }

    public static JsonObject postRequest(String url, JsonElement json) throws IOException {
        HttpUriRequest request = RequestBuilder.post()
                .setUri(url)
                .addHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(NetworkUtils.GSON.toJson(json), StandardCharsets.UTF_8))
                .build();
        return postRequest(request);
    }

    public static JsonObject postRequest(String url, Map<String, String> formData, boolean ignoreHttpStatus) throws IOException {
        String body = encodeForm(formData);
        HttpUriRequest request = RequestBuilder.post()
                .setUri(url)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setEntity(new StringEntity(body, StandardCharsets.UTF_8))
                .build();
        return postRequest(request, ignoreHttpStatus);
    }

    public static JsonObject postRequest(String url, Map<String, String> formData) throws IOException {
        return postRequest(url, formData, false);
    }

    private static String encodeForm(Map<String, String> formData) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEncode(entry.getKey()));
            sb.append('=');
            sb.append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public static Reader readResponse(HttpResponse response, boolean ignoreHttpStatus) throws IOException {
        if (!ignoreHttpStatus) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode / 100 != 2) {
                throw new IOException("HTTP " + statusCode + ": " + response.getStatusLine().getReasonPhrase());
            }
        }

        Charset charset = StandardCharsets.UTF_8;
        Header contentType = response.getEntity().getContentType();
        if (contentType != null) {
            String ct = contentType.getValue().toLowerCase(Locale.ROOT);
            int idx = ct.indexOf("charset=");
            if (idx != -1) {
                String cs = ct.substring(idx + 8).trim();
                int semi = cs.indexOf(';');
                if (semi != -1) cs = cs.substring(0, semi);
                cs = cs.replace("\"", "").trim();
                try {
                    charset = Charset.forName(cs);
                } catch (Exception ignored) {
                }
            }
        }

        return new InputStreamReader(response.getEntity().getContent(), charset);
    }
}
