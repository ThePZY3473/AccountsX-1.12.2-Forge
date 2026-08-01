package top.syshub.accountsx.core.accounts.model.context;

import java.net.IDN;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface SkinURLVerifier {
    boolean isSkinURLSecure(URI uri);

    Set<String> ALLOWED_SCHEMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("http", "https")));

    SkinURLVerifier MOJANG_DEFAULT = ofDomainVerifier(
            Arrays.asList(".minecraft.net", ".mojang.com"),
            Arrays.asList("bugs.mojang.com", "education.minecraft.net", "feedback.minecraft.net")
    );

    static SkinURLVerifier ofOperationOR(SkinURLVerifier v1, SkinURLVerifier v2) {
        return uri -> v1.isSkinURLSecure(uri) || v2.isSkinURLSecure(uri);
    }

    static SkinURLVerifier ofDomainVerifier(List<String> allowedDomains, List<String> blockedDomains) {
        return new SkinURLVerifier() {
            @Override
            public boolean isSkinURLSecure(URI uri) {
                String scheme = uri.getScheme();
                if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
                    return false;
                }
                String domain = uri.getHost();
                if (domain == null) {
                    return false;
                }
                String decodedDomain = IDN.toUnicode(domain);
                if (!decodedDomain.toLowerCase(Locale.ROOT).equals(decodedDomain)) {
                    return false;
                }
                return isDomainOnList(decodedDomain, allowedDomains) && !isDomainOnList(decodedDomain, blockedDomains);
            }
        };
    }

    static boolean isDomainOnList(final String domain, final List<String> list) {
        for (final String entry : list) {
            if (domain.endsWith(entry)) {
                return true;
            }
        }
        return false;
    }
}
