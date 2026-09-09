package StyleUI;

import java.lang.reflect.Method;
import java.util.function.*;

final class LocalizationBridge {
    private static final boolean AVAILABLE;
    private static final Method BIND, BIND_FORMAT, UNBIND, EXTERNAL_TEXT_CHANGED, LOCALIZED, ADD_LISTENER,  REMOVE_LISTENER;
    private LocalizationBridge() {}

    static {
        boolean available;
        Method bind = null, bindFormat = null, unbind = null, externalTextChanged = null, localized = null, addListener = null, removeListener = null;
        try {
            Class<?> cls = Class.forName("Helpers.NSLocalizableString");
            bind = cls.getMethod("bind", Object.class, String.class, Consumer.class);
            bindFormat = cls.getMethod("bindFormat", Object.class, String.class, Supplier.class, Consumer.class);
            unbind = cls.getMethod("unbind", Object.class);
            externalTextChanged = cls.getMethod("externalTextChanged", Object.class, String.class, Consumer.class);
            localized = cls.getMethod("localized", String.class);
            addListener = cls.getMethod("addLanguageChangeListener", Runnable.class);
            removeListener = cls.getMethod("removeLanguageChangeListener", Runnable.class);
            available = true;
        } catch (ReflectiveOperationException | LinkageError e) { available = false; }
        AVAILABLE = available;
        BIND = bind;
        BIND_FORMAT = bindFormat;
        UNBIND = unbind;
        EXTERNAL_TEXT_CHANGED = externalTextChanged;
        LOCALIZED = localized;
        ADD_LISTENER = addListener;
        REMOVE_LISTENER = removeListener;
    }
    static boolean isAvailable() { return AVAILABLE; }

    static void bind(Object owner, String key, Consumer<String> setter) {
        if (!AVAILABLE) { applyPlain(setter, key); return; }
        try { BIND.invoke(null, owner, key, setter); }
        catch (ReflectiveOperationException ignored) { applyPlain(setter, key); }
    }
    static void bindFormat(Object owner, String key, Supplier<Object[]> arguments, Consumer<String> setter) {
        if (!AVAILABLE) { applyPlainFormat(setter, key, arguments); return; }
        try { BIND_FORMAT.invoke(null, owner, key, arguments, setter); }
        catch (ReflectiveOperationException ignored) { applyPlainFormat(setter, key, arguments); }
    }
    static void unbind(Object owner) {
        if (!AVAILABLE) return;
        try { UNBIND.invoke(null, owner); } catch (ReflectiveOperationException ignored) {}
    }
    static void externalTextChanged(Object owner, String source, Consumer<String> setter) {
        if (!AVAILABLE) { applyPlain(setter, source); return; }
        try { EXTERNAL_TEXT_CHANGED.invoke(null, owner, source, setter); }
        catch (ReflectiveOperationException ignored) { applyPlain(setter, source); }
    }
    static String localized(String key) {
        if (!AVAILABLE) return key == null ? "" : key;
        try {
            Object result = LOCALIZED.invoke(null, key);
            return result == null ? "" : (String) result;
        } catch (ReflectiveOperationException ignored) { return key == null ? "" : key; }
    }
    static void addLanguageChangeListener(Runnable listener) {
        if (!AVAILABLE) return;
        try { ADD_LISTENER.invoke(null, listener); } catch (ReflectiveOperationException ignored) {}
    }
    static void removeLanguageChangeListener(Runnable listener) {
        if (!AVAILABLE) return;
        try { REMOVE_LISTENER.invoke(null, listener); } catch (ReflectiveOperationException ignored) {}
    }
    private static void applyPlain(Consumer<String> setter, String value) {
        if (setter != null) setter.accept(value == null ? "" : value);
    }
    private static void applyPlainFormat(Consumer<String> setter, String key, Supplier<Object[]> arguments) {
        if (setter == null) return;
        try { setter.accept(String.format(key, arguments != null ? arguments.get() : new Object[0])); }
        catch (RuntimeException e) { setter.accept(key == null ? "" : key); }
    }
}