package org.jboss.perf.hibernate;

import org.jboss.byteman.rule.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InstanceTracer {
    private static boolean tracing = true;
    private static final HashMap<String, Invocations> map;
    private static final ThreadLocal<List<Object>> constructed;

    static {
        Rule.disableTriggers();
        map = new HashMap<>();
        constructed = new ThreadLocal<List<Object>>() {
            @Override
            protected List<Object> initialValue() {
                return new LinkedList<>();
            }
        };
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

        public void deregister(StackTraceElement[] st) {
            Counter counter = invocations.get(st);
            // if counter is not found, the autoboxing was caused by caller,
            // not by Byteman wrapping the primitive arguments
            if (counter != null) {
                counter.counter--;
                totalInvocations--;
            }
        }
    }

    private static class StackTraceComparator implements Comparator<StackTraceElement[]> {
        public final static StackTraceComparator INSTANCE = new StackTraceComparator(5);

        private final int stackOffset;

        private StackTraceComparator(int stackOffset) {
            this.stackOffset = stackOffset;
        }

        @Override
        public int compare(StackTraceElement[] o1, StackTraceElement[] o2) {
            if (o1.length < o2.length) return -1;
            if (o1.length > o2.length) return 1;
            for (int i = stackOffset; i < o1.length; ++i) {
                if (o1[i] == null || o2[i] == null) {
                    // this is a specially forged stack
                    continue;
                }
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

    public static synchronized void createEntry(String className, Object[] arguments) {
        if (tracing) {
            tracing = false;
            Rule.disableTriggers();
            try {
                StackTraceElement[] stackTrace = new Exception().getStackTrace();
                if (arguments != null) {
                    decrementPrimitiveInvocations(arguments, stackTrace);

                    Object self = arguments[0];
                    if (!className.equals(self.getClass().getName())) {
                        return;
                    }
                    List<Object> list = constructed.get();
                    for (Iterator<Object> iterator = list.iterator(); iterator.hasNext(); ) {
                        if (iterator.next() == self) return;
                    }
                    list.add(self);
                }

                Invocations invocations = map.get(className);
                if (invocations == null) {
                    map.put(className, new Invocations(stackTrace));
                } else {
                    invocations.register(stackTrace);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                Rule.enableTriggers();
                tracing = true;
            }
        }
    }

    public static synchronized void createExit(String className, Object[] arguments) {
        if (tracing) {
            tracing = false;
            Rule.disableTriggers();
            try {
                if (arguments == null) return;
                StackTraceElement[] stackTrace = new Exception().getStackTrace();
                decrementPrimitiveInvocations(arguments, stackTrace);

                Object self = arguments[0];
                if (!className.equals(self.getClass().getName())) {
                    return;
                }
                for (Iterator<Object> iterator = constructed.get().iterator(); iterator.hasNext(); ) {
                    if (iterator.next() == self) {
                        iterator.remove();
                        return;
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

    private static void decrementPrimitiveInvocations(Object[] arguments, StackTraceElement[] stackTrace) {
        // unroll this constructor from the stack trace
        StackTraceElement[] forgedStack = new StackTraceElement[stackTrace.length + 1];
        System.arraycopy(stackTrace, 5, forgedStack, 6, stackTrace.length - 5);
        for (int i = 1; i < arguments.length; ++i) {
            Object arg = arguments[i];
            if (arg != null && Primitives.CLASS_SET.contains(arg.getClass())) {
                Invocations primitiveInvocations = map.get(arg.getClass().getName());
                if (primitiveInvocations != null) {
                    primitiveInvocations.deregister(forgedStack);
                }
            }
        }
    }

    public static synchronized void resetStats() {
        InstanceTracer.map.clear();
    }

    public static synchronized void printStats(boolean printStackTraces, int invocations) {
        tracing = false;
        ArrayList<Map.Entry<String, Invocations>> sorted = new ArrayList<>(InstanceTracer.map.entrySet());
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
                if (invocations > 0) {
                    System.out.printf("%4.2f/op (%7dx) %s%n", (double) value / invocations, value, entry.getKey());
                } else {
                    System.out.printf("%7dx %s%n", value, entry.getKey());
                }
                if (printStackTraces) {
                    for (Map.Entry<StackTraceElement[], Counter> inv : entry.getValue().invocations.entrySet()) {
                        if (inv.getValue().counter == 0) continue;
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
