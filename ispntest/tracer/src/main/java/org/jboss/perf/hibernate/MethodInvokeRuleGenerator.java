package org.jboss.perf.hibernate;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Generates rules for counting method invocations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MethodInvokeRuleGenerator extends RuleGenerator {
   private static final String TRACER_CLASS_NAME = MethodInvokeTracer.class.getName();
   private int ruleCounter;

   @Override
   public void printRules(Class<?> clazz, String className, PrintStream stream) {
      for (Method m : clazz.getDeclaredMethods()) {
         if (m.isSynthetic() || m.isBridge()) continue;
         int modifiers = m.getModifiers();
         if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) continue;
         StringBuilder sb = new StringBuilder();
         int ruleNumber = ruleCounter++;
         if (ruleNumber >= MethodInvokeTracer.MAX_TRACED_METHODS) {
            throw new IllegalStateException("Too many rules!");
         }
         sb.append("RULE r").append(ruleNumber).append('\n');
         sb.append("CLASS ").append(className).append('\n');
         sb.append("METHOD ").append(m.getName()).append('(');
         boolean first = true;
         for (Class<?> c : m.getParameterTypes()) {
            if (first) {
               first = false;
            } else {
               sb.append(',');
            }
            sb.append(c.getName());
         }
         sb.append(")\nAT ENTRY\nIF TRUE\nDO\n");
         sb.append(TRACER_CLASS_NAME).append(".invoked(").append(ruleNumber).append(");\nENDRULE\n\n");
         stream.print(sb.toString());
      }
   }

   @Override
   public long getNumWrittenRules() {
      return ruleCounter;
   }

   public static void main(String[] args) {
      ClassWalker.walk(args, new MethodInvokeRuleGenerator());
   }
}
