package org.jboss.perf.hibernate;

import java.io.PrintStream;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class RuleGenerator {
   public abstract void printRules(Class<?> clazz, String className, PrintStream stream);
   public void printCustomRules(PrintStream stream) {}
   public abstract long getNumWrittenRules();
}
