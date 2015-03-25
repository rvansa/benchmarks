package org.jboss.perf.hibernate;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Primitives {
    public static final Class<?>[] CLASSES = new Class<?>[] {
          Byte.class, Boolean.class, Character.class, Double.class,
          Float.class, Integer.class, Long.class, Short.class
    };
    public static final String[] NAMES;
    public static final Set<String> NAME_SET;
    public static final Set<Class<?>> CLASS_SET;

    static {
        NAMES = new String[CLASSES.length];
        NAME_SET = new HashSet<>();
        CLASS_SET = new HashSet<>();
        int i = 0;
        for (Class<?> c : CLASSES) {
            String name = c.getName();
            NAMES[i] = name;
            NAME_SET.add(name);
            CLASS_SET.add(c);
            ++i;
        }
    }
}
