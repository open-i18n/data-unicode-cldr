package org.unicode.cldr.draft;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.icu.text.Transform;

public class EnumLookup<T extends Enum<?>> {
    private Map<String,T> map = new HashMap<String,T>();
    private Transform<String,String> transform;
    public static <T extends Enum<?>> EnumLookup<T> of(Class<T> className) {
        return of(className, null, null);
    }
    public static <T extends Enum<?>> EnumLookup<T> of(Class<T> className, Transform<String,String> t, String from, T to, Object... extras) {
        Map<String,T> newExtras = new HashMap<String,T>();
        newExtras.put(from, to);
        for (int i = 0; i < extras.length; i += 2) {
            newExtras.put((String)extras[i], (T)extras[i+1]);
        }
        return of(className, t, newExtras);
    }
    public static <T extends Enum<?>> EnumLookup<T> of(Class<T> className, Transform<String,String> t, Map<String,T> extras) {
        EnumLookup<T> result = new EnumLookup<T>();
        try {
            result.transform = t = t == null ? CLEAN : t;
            Method m = className.getMethod("values",(Class<?>[])null);
            T[] values = (T[]) m.invoke(null);
            for (T value : values) {
                result.map.put(t.transform(value.name()), value);
                result.map.put(t.transform(value.toString()), value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        if (extras == null) {
            return result;
        }
        for (Entry<String, T> entry : extras.entrySet()) {
            final String key = t.transform(entry.getKey());
            final T newValue = entry.getValue();
            final T old = result.map.get(key);
            if (old == null) {
                result.map.put(key, newValue);
            } else if (old != newValue) {
                throw new IllegalArgumentException("Incompatible mapping: " + key + "=" + old + "!=" + newValue);
            }
        }
        return result;
    }
    private static Transform<String,String> CLEAN = new Transform<String,String>() {
        @Override
        public  String transform(String in) {
            return in.toUpperCase(Locale.ENGLISH).replace(' ', '_');
        }
    };
    public T forString(String s) {
        return forString(s, false);
    }
    public T forString(String s, boolean allowNull) {
        T result = map.get(transform.transform(s));
        if (!allowNull && result == null) {
            throw new IllegalArgumentException("Can't find match for " + s + " in " + map.keySet());
        }
        return result;
    }
    private EnumLookup() {}
}