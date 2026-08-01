package top.syshub.accountsx.core.accounts.impl.injector;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public class OAuthConstants {
    public static final Map<String, String> list;

    static {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("skin.jsumc.fun", "3");
        values.put("skin.mualliance.ltd", "28");
        values.put("littleskin.cn", "1214");
        values.put("mcskin.ecustvr.top", "6");
        list = Collections.unmodifiableMap(values);
    }
}
