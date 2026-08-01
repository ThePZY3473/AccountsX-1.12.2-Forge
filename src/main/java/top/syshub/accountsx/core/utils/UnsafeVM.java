package top.syshub.accountsx.core.utils;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import top.syshub.accountsx.core.AccountsX;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class UnsafeVM {
    private UnsafeVM() {
    }

    private static final MethodHandles.Lookup GENERAL_LOOKUP = MethodHandles.lookup();

    @SuppressWarnings("deprecation")
    private static final Supplier<MethodHandles.Lookup> IMPL_LOOKUP = Suppliers.memoize(new Supplier<MethodHandles.Lookup>() {
        @Override
        public MethodHandles.Lookup get() {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                Object unsafe = theUnsafe.get(null);
                Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                Method staticFieldBase = unsafeClass.getMethod("staticFieldBase", Field.class);
                Method staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field.class);
                Method getObject = unsafeClass.getMethod("getObject", Object.class, long.class);
                Object base = staticFieldBase.invoke(unsafe, implLookup);
                long offset = ((Long) staticFieldOffset.invoke(unsafe, implLookup)).longValue();
                return (MethodHandles.Lookup) getObject.invoke(unsafe, base, offset);
            } catch (Throwable t) {
                return GENERAL_LOOKUP;
            }
        }
    });

    private static final ConcurrentHashMap<Class<?>, MethodHandle> UNSAFE_ALLOCATOR_CACHE = new ConcurrentHashMap<Class<?>, MethodHandle>();

    public static MethodHandles.Lookup getLookup() {
        return IMPL_LOOKUP.get();
    }

    public static MethodHandle getClassAllocator(Class<?> clazz) {
        MethodHandle allocator = UNSAFE_ALLOCATOR_CACHE.get(clazz);
        if (allocator != null) {
            return allocator;
        }

        try {
            allocator = GENERAL_LOOKUP.findStatic(UnsafeVM.class, "allocateInstance", MethodType.methodType(Object.class, Class.class))
                    .bindTo(clazz)
                    .asType(MethodType.methodType(clazz));
            MethodHandle existing = UNSAFE_ALLOCATOR_CACHE.putIfAbsent(clazz, allocator);
            return existing == null ? allocator : existing;
        } catch (Throwable t) {
            throw fail("Unsafe::allocateInstance", t);
        }
    }

    private static Object allocateInstance(Class<?> clazz) throws Throwable {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, clazz);
    }

    public interface MethodHandleProvider {
        MethodHandle compute(MethodHandles.Lookup lookup) throws ReflectiveOperationException;
    }

    public static MethodHandle prepareMH(String target, MethodHandleProvider provider) {
        try {
            return provider.compute(getLookup());
        } catch (Throwable t) {
            throw fail(target, t);
        }
    }

    private static final class UnexpectedClassChangeError extends Error {
        public UnexpectedClassChangeError(String message, Throwable[] ts) {
            super(message);

            for (Throwable t : ts) {
                addSuppressed(t);
            }
        }

        public UnexpectedClassChangeError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Error fail(String hackTarget, Throwable t) {
        return new UnexpectedClassChangeError("Cannot hack " + hackTarget + " due to unexpected changes. Please remove " + AccountsX.MOD_NAME, t);
    }

    public static Error fail(String hackTarget, Throwable... t) {
        return new UnexpectedClassChangeError("Cannot hack " + hackTarget + " due to unexpected changes. Please remove " + AccountsX.MOD_NAME, t);
    }
}
