package top.syshub.accountsx.core.utils.access;

import top.syshub.accountsx.core.utils.UnsafeVM;

import java.lang.invoke.MethodHandles;

public final class UnsafeLookupAccessor {
    private UnsafeLookupAccessor() {
    }

    public static MethodHandles.Lookup get() throws Throwable {
        return UnsafeVM.getLookup();
    }
}
