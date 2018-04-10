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
public class ClassWalker {
    private final static String RULE_CLASS = "org.jboss.byteman.rule.Rule";
    private static final ArrayList<String> EXCLUDED_PREFIXES = new ArrayList<>();
    private static final ArrayList<String> EXCLUDED_SUFFIXES = new ArrayList<>();
    private static final ArrayList<String> INCLUDED_REGEXPS = new ArrayList<>();

    public static void walk(String[] args, RuleGenerator generator) {
         ArrayList<String> files = new ArrayList<>();
         ArrayList<URL> urls = new ArrayList<>();
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
                             generator.printRules(clazz, name, stream);
                         } catch (ClassNotFoundException e1) {
                             e1.printStackTrace();
                         } catch (NoClassDefFoundError e2) {
                             // constructor exists but argument is not on classpath
                         } catch (Throwable e3) {
                             System.err.printf("Failed to load %s%n", name);
                             e3.printStackTrace();
                         }
                     }
                 }
             }
             System.out.printf("Written %d rules\n", generator.getNumWrittenRules());
         } catch (IOException e) {
             e.printStackTrace();
         }
     }


}

