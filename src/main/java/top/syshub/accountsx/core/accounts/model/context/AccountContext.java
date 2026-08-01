package top.syshub.accountsx.core.accounts.model.context;

public final class AccountContext {
    private final AuthServerContext server;
    private final AuthSecurityContext security;
    private final AuthPolicy policy;

    public AccountContext(AuthServerContext server, AuthSecurityContext security, AuthPolicy policy) {
        this.server = server;
        this.security = security;
        this.policy = policy;
    }

    public AuthServerContext server() {
        return server;
    }

    public AuthSecurityContext security() {
        return security;
    }

    public AuthPolicy policy() {
        return policy;
    }
}
