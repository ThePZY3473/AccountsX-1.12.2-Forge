package top.syshub.accountsx.core.adapters;

import com.google.common.base.Suppliers;
import top.syshub.accountsx.core.AccountsX;
import top.syshub.accountsx.core.adapters.api.AccountSession;
import top.syshub.accountsx.core.adapters.api.AuthlibAdapter;
import top.syshub.accountsx.core.adapters.api.MinecraftAdapter;
import top.syshub.accountsx.forge.authlib.AuthlibAdapterImpl;
import top.syshub.accountsx.forge.mc.MinecraftAdapterImpl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import com.google.common.base.Supplier;

public final class Adapters {
    private Adapters() {
    }

    private static final class AdapterImpl {
        private final AuthlibAdapter<AccountSession> authlibAdapter;
        private final MinecraftAdapter<AccountSession> minecraftAdapter;

        private AdapterImpl(AuthlibAdapter<AccountSession> authlibAdapter,
                            MinecraftAdapter<AccountSession> minecraftAdapter) {
            this.authlibAdapter = authlibAdapter;
            this.minecraftAdapter = minecraftAdapter;
            if (!Arrays.equals(
                    getAccountSessionType(authlibAdapter, AuthlibAdapter.class),
                    getAccountSessionType(minecraftAdapter, MinecraftAdapter.class)
            )) {
                throw new IllegalStateException("Unmatched adapters!");
            }
        }

        private static <T> Type[] getAccountSessionType(T o, Class<T> apiClass) {
            Type[] adapterTypes = o.getClass().getGenericInterfaces();
            for (Type adapterType : adapterTypes) {
                if (adapterType == apiClass) {
                    throw new IllegalStateException(String.format("%s should directly implement %s and provide a generic argument.", o.getClass(), apiClass));
                }

                if (adapterType instanceof ParameterizedType && ((ParameterizedType) adapterType).getRawType() == apiClass) {
                    ParameterizedType pAdapterType = (ParameterizedType) adapterType;
                    return pAdapterType.getActualTypeArguments();
                }
            }

            throw new IllegalStateException(String.format("%s should directly implement %s.", o.getClass(), apiClass));
        }

        private AuthlibAdapter<AccountSession> authlibAdapter() {
            return authlibAdapter;
        }

        private MinecraftAdapter<AccountSession> minecraftAdapter() {
            return minecraftAdapter;
        }
    }

    @SuppressWarnings({"unchecked"})
    private static final Supplier<AdapterImpl> INSTANCE = Suppliers.memoize(new Supplier<AdapterImpl>() {
        @Override
        public AdapterImpl get() {
            return new AdapterImpl(
                    (AuthlibAdapter<AccountSession>) (AuthlibAdapter<?>) new AuthlibAdapterImpl(),
                    (MinecraftAdapter<AccountSession>) (MinecraftAdapter<?>) new MinecraftAdapterImpl()
            );
        }
    });

    public static AuthlibAdapter<AccountSession> getAuthlibAdpater() {
        return INSTANCE.get().authlibAdapter();
    }

    public static MinecraftAdapter<AccountSession> getMinecraftAdapter() {
        return INSTANCE.get().minecraftAdapter();
    }

}
