package top.syshub.accountsx.core.accounts.model.context;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.PublicKey;
import java.util.List;

public final class AuthSecurityContext {
    private final List<PublicKey> profilePropertyKeys;
    private final List<PublicKey> playerCertificateKeys;
    private final SkinURLVerifier skinURLVerifier;

    public AuthSecurityContext(List<PublicKey> profilePropertyKeys, List<PublicKey> playerCertificateKeys,
                               SkinURLVerifier skinURLVerifier) {
        this.profilePropertyKeys = profilePropertyKeys;
        this.playerCertificateKeys = playerCertificateKeys;
        this.skinURLVerifier = skinURLVerifier;
    }

    public AuthSecurityContext(List<PublicKey> profilePropertyKeys, List<PublicKey> playerCertificateKeys) {
        this(profilePropertyKeys, playerCertificateKeys, SkinURLVerifier.MOJANG_DEFAULT);
    }

    public List<PublicKey> profilePropertyKeys() {
        return profilePropertyKeys;
    }

    public List<PublicKey> playerCertificateKeys() {
        return playerCertificateKeys;
    }

    public SkinURLVerifier skinURLVerifier() {
        return skinURLVerifier;
    }

    public boolean checkSkinURL(String url) {
        URI uri;
        try {
            uri = new URI(url).normalize();
        } catch (URISyntaxException ignored) {
            return true;
        }

        return !skinURLVerifier.isSkinURLSecure(uri);
    }
}
