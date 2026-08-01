package top.syshub.accountsx.core.accounts.model.context;

public final class AuthServerContext {
    private final String authURL;
    private final String accountURL;
    private final String sessionURL;
    private final String serviceURL;
    private final String name;

    public AuthServerContext(String authURL, String accountURL, String sessionURL, String serviceURL, String name) {
        this.authURL = authURL;
        this.accountURL = accountURL;
        this.sessionURL = sessionURL;
        this.serviceURL = serviceURL;
        this.name = name;
    }

    public String authURL() {
        return authURL;
    }

    public String accountURL() {
        return accountURL;
    }

    public String sessionURL() {
        return sessionURL;
    }

    public String serviceURL() {
        return serviceURL;
    }

    public String name() {
        return name;
    }
}
