package org.jboss.perf.hibernate;

import java.io.PrintStream;

/**
 * Generates rule for counting of created instances
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InstanceRuleGenerator extends RuleGenerator {
   private final static String TRACER_CLASS = InstanceTracer.class.getName();
   private long ruleCounter = 0;

   private String getRules(String name, String arguments) {
      StringBuilder sb = new StringBuilder();

      sb.append("RULE ").append("e").append(ruleCounter++).append('\n');
      sb.append("CLASS ").append(name).append('\n');
      sb.append("METHOD <init>\nAT ENTRY\nIF true\nDO\n");
      sb.append(TRACER_CLASS).append(".createEntry(\"").append(name).append("\", ").append(arguments).append(");\n");
      sb.append("ENDRULE\n\n");

      sb.append("RULE ").append("x").append(ruleCounter++).append('\n');
      sb.append("CLASS ").append(name).append('\n');
      sb.append("METHOD <init>\nAT EXIT\nIF true\nDO\n");
      sb.append(TRACER_CLASS).append(".createExit(\"").append(name).append("\", ").append(arguments).append(");\n");
      sb.append("ENDRULE\n\n");

      return sb.toString();
   }

   @Override
   public void printRules(Class<?> clazz, String className, PrintStream stream) {
      if (Primitives.NAME_SET.contains(className)) {
         return;
      } else if (clazz.getDeclaredConstructors().length == 0) {
         return;
      }
      stream.print(getRules(className, "$*"));
   }

   @Override
   public void printCustomRules(PrintStream stream) {
      // primitives needs special handling due to boxing
      for (String primitive : Primitives.NAMES) {
         //stream.print(getRules(primitive, "new Object[] { $this }"));
         stream.print(getRules(primitive, "null"));
      }
   }

   @Override
   public long getNumWrittenRules() {
      return ruleCounter;
   }

   public static void main(String[] args) {
      ClassWalker.walk(args, new InstanceRuleGenerator());
   }
}
