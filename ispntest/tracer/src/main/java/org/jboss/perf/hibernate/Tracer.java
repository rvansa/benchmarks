package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.jboss.byteman.rule.Rule;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Tracer {
    private static boolean tracing = true;
    private static final HashMap<String, Invocations> map;

    static {
        Rule.disableTriggers();
        map = new HashMap<>();
        Rule.enableTriggers();
    }

    private static class Counter {
        public int counter;

        public Counter(int counter) {
            this.counter = counter;
        }
    }

    private static class Invocations {
        TreeMap<StackTraceElement[], Counter> invocations = new TreeMap<>(StackTraceComparator.INSTANCE);
        int totalInvocations = 0;

        public Invocations(StackTraceElement[] stackTrace) {
            register(stackTrace);
        }

        public void register(StackTraceElement[] st) {
            Counter counter = invocations.get(st);
            if (counter == null) {
                invocations.put(st, new Counter(1));
            } else {
                counter.counter++;
            }
            ++totalInvocations;
        }
    }

    private static class StackTraceComparator implements Comparator<StackTraceElement[]> {
        public final static StackTraceComparator INSTANCE = new StackTraceComparator();

        private StackTraceComparator() {}

        @Override
        public int compare(StackTraceElement[] o1, StackTraceElement[] o2) {
            if (o1.length < o2.length) return -1;
            if (o1.length > o2.length) return 1;
            for (int i = 5; i < o1.length; ++i) {
                int comp = o1[i].getClassName().compareTo(o2[i].getClassName());
                if (comp != 0) return comp;
                comp = o1[i].getMethodName().compareTo(o2[i].getMethodName());
                if (comp != 0) return comp;
                comp = Integer.compare(o1[i].getLineNumber(), o2[i].getLineNumber());
                if (comp != 0) return comp;
            }
            return 0;
        }
    }


    public static synchronized void created(String className) {
        if (tracing) {
            tracing = false;
            Rule.disableTriggers();
            try {
                StackTraceElement[] stackTrace = new Exception().getStackTrace();
                if (stackTrace.length > 6 && className.equals(stackTrace[6].getClassName()) && "<init>".equals(stackTrace[6].getMethodName())) {
                    // this constructor is invoked using this(...);
                    if (stackTrace[6].getLineNumber() != stackTrace[5].getLineNumber()) {
                        // for default constructors, sometimes the constructor calls itself twice...
                        return;
                    }
                }
                HashMap<String, Invocations> map = Tracer.map;
                Invocations invocations = map.get(className);
                if (invocations == null) {
                    map.put(className, new Invocations(stackTrace));
                } else {
                    invocations.register(stackTrace);
                }
                // decrease count for base classes
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                if (loader != null) {
                    try {
                        Class<?> clazz = loader.loadClass(className);
                        Class<?> superClazz = clazz.getSuperclass();
                        if (superClazz != null && superClazz != Object.class) {
                            invocations = map.get(superClazz.getName());
                            if (invocations == null) {
                                ClassLoader superClassLoader = superClazz.getClassLoader();
                                if (!superClazz.isEnum() && superClassLoader != null) { // ignore boot class loader
                                    System.err.printf("%04x: Counter for %s loaded by %s not found (from %s)%n", Thread.currentThread().getId(), superClazz.getName(), superClassLoader, className);
                                    for (StackTraceElement ste : stackTrace) {
                                        System.err.printf("   %s.%s%n", ste.getClassName(), ste.getMethodName());
                                    }
                                }
                            } else {
                                long cvalue = --invocations.totalInvocations;
                                if (cvalue < 0) {
                                    System.err.printf("%04x, Counter %d < 0 for %s (from %s)%n", Thread.currentThread().getId(), cvalue, superClazz.getName(), className);
                                }
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        System.err.println(e.getMessage());
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                Rule.enableTriggers();
                tracing = true;
            }
        }
    }

    public static synchronized void resetStats() {
        Tracer.map.clear();
    }

    public static synchronized void printStats(boolean printStackTraces) {
        tracing = false;
        ArrayList<Map.Entry<String, Invocations>> sorted = new ArrayList<>(Tracer.map.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Invocations>>() {
            @Override
            public int compare(Map.Entry<String, Invocations> o1, Map.Entry<String, Invocations> o2) {
                int comp = -Integer.compare(o1.getValue().totalInvocations, o2.getValue().totalInvocations);
                if (comp != 0) return comp;
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        if (!map.isEmpty()) System.out.print("\n\n---------------\n");
        for (Map.Entry<String, Invocations> entry : sorted) {
            int value = entry.getValue().totalInvocations;
            if (value != 0) {
                System.out.printf("%7dx %s%n", value, entry.getKey());
                if (printStackTraces) {
                    for (Map.Entry<StackTraceElement[], Counter> inv : entry.getValue().invocations.entrySet()) {
                        System.out.printf("\tInvoked %d times from:%n", inv.getValue().counter);
                        StackTraceElement[] stackTrace = inv.getKey();
                        for (int i = 5; i < stackTrace.length; ++i) {
                            System.out.printf("\t\t%s%n", stackTrace[i]);
                        }
                    }
                }
            }
        }
        if (!map.isEmpty()) System.out.print("---------------\n");
        tracing = true;
    }
}
