This project tries to:

a) microbenchmark Infinispan's cache.get() and cache.put() operations
b) find out allocation rate during these operations

For a), simply run as any other JMH benchmark.
b) requires several steps, the rest of this document shows how to do that.

0. mvn install

1. You need to generate Byteman rules
Byteman cannot instrument java.lang.Object's constructor and CLASS ^java.lang.Object METHOD <init>
hits some objects whose instrumentation would cause stack overflow during runtime. Therefore we have
provided RuleGenerator which creates file rules.btm with separate rule for each class (except those
problematic ones). To use it, run

$JAVA_HOME/bin/java -cp perftest/target/perftest-1.0-SNAPSHOT.jar:tracer/target/tracer-1.0-SNAPSHOT.jar \
    org.jboss.perf.hibernate.RuleGenerator perftest/target/benchmarks.jar $JAVA_HOME/jre/lib/rt.jar \
    -ep 'java.lang.ThreadLocal$ThreadLocalMap' -ep java.lang.ref.WeakReference -ep java.lang.ref.Reference

Make sure $JAVA_HOME points to the JDK which you later want to use for the test.

2. Run the test, with Byteman rules (rules.btm) generated in previous step:

$JAVA_HOME/bin/java -Dorg.jboss.byteman.transform.all -Dorg.jboss.byteman.compileToBytecode=true \
    -javaagent:${BYTEMAN_HOME}/lib/byteman.jar=script:rules.btm,boot:${BYTEMAN_HOME}/lib/byteman.jar,boot:tracer/target/tracer-1.0-SNAPSHOT.jar \
    -jar perftest/target/benchmarks.jar -i 1 -wi 1 -f 0

If you want to show the stack traces for allocations, add -Dtracer.printStackTraces=true

Limitations:
- Arrays construction is not instrumented (AFAIK no way to do that in Byteman)
- java.lang.Object itself, and the excluded classes above are not instrumented
- Constructors are called for each superclass too, therefore, the concrete class has to decrement
  parent's constructor counter. However, it does not decrement the counter in stacktrace listing.
- When calling another constructor using this(...) in constructor, the object would be counted twice.
  There is a hackish check against it, but it may not work in 100% cases.

Feel free to reuse this for your purposes.
