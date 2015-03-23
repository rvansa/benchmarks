package org.jboss.perf.hibernate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates rule file
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RuleGenerator {
    private final static String TRACER_CLASS = Tracer.class.getName();
    private static final ArrayList<String> EXCLUDED_PREFIXES = new ArrayList<>();
    private static final ArrayList<String> EXCLUDED_SUFFIXES = new ArrayList<>();
    private static final ArrayList<String> INCLUDED_REGEXPS = new ArrayList<>();

    public static void main(String[] args) {
         ArrayList<String> files = new ArrayList<>();
         ArrayList<URL> urls = new ArrayList<>();
         int ruleCounter = 0;
         ConcurrentHashMap<String, String> processed = new ConcurrentHashMap<>();
         for (int i = 0; i < args.length; ++i) {
             switch (args[i]) {
                 case "-ep":
                     ++i;
                     EXCLUDED_PREFIXES.add(args[i]);
                     break;
                 case "-es":
                     ++i;
                     EXCLUDED_SUFFIXES.add(args[i]);
                     break;
                 case "-ir":
                     ++i;
                     INCLUDED_REGEXPS.add(args[i]);
                     break;
                 default:
                     files.add(args[i]);
                     try {
                         urls.add(new File(args[i]).toURI().toURL());
                     } catch (MalformedURLException e) {
                         e.printStackTrace();
                     }
                     break;
             }
         }
         URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
         try (FileOutputStream fos = new FileOutputStream("rules.btm");
              PrintStream stream = new PrintStream(fos)) {
             for (String file : files) {
                 try (ZipFile zipFile = new ZipFile(file)) {
                     for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
                         ZipEntry entry = e.nextElement();
                         if (!entry.getName().endsWith(".class")) {
                             continue;
                         }
                         String slashName = entry.getName().substring(0, entry.getName().length() - 6);
                         String name = slashName.replace('/', '.');
                         boolean excluded = false;
                         for (String prefix : EXCLUDED_PREFIXES) {
                             if (name.startsWith(prefix)) {
                                 excluded = true;
                             }
                         }
                         for (String suffix : EXCLUDED_SUFFIXES) {
                             if (name.endsWith(suffix)) {
                                 excluded = true;
                             }
                         }
                         for (String regexp : INCLUDED_REGEXPS) {
                             if (name.matches(regexp)) {
                                 excluded = false;
                             }
                         }
                         if (excluded) {
                             continue;
                         }
                         if (processed.containsKey(name)) {
                             continue;
                         } else {
                             processed.put(name, file);
                         }
                         try {
                             Class<?> clazz = classLoader.loadClass(name);
                             if (clazz.getDeclaredConstructors().length == 0) {
                                 continue;
                             }
                         } catch (ClassNotFoundException e1) {
                             e1.printStackTrace();
                             continue;
                         } catch (NoClassDefFoundError e2) {
                             // constructor exists but argument is not on classpath
                         }

                         StringBuilder sb = new StringBuilder();
                         sb.append("# ").append(++ruleCounter).append(" ").append(file).append("\n");
                         sb.append("RULE ").append("r_").append(name.replace('.', '_').replace('$', '_')).append('\n');
                         sb.append("CLASS ").append(name).append('\n');
                         sb.append("METHOD <init>\nAT ENTRY\nIF true\nDO\n");
                         sb.append(TRACER_CLASS).append(".created(\"").append(name).append("\");\n");
                         sb.append("ENDRULE\n\n");
                         stream.print(sb.toString());
                     }
                 }
             }
             System.out.printf("Written %d rules\n", ruleCounter);
         } catch (IOException e) {
             e.printStackTrace();
         }
     }
}

