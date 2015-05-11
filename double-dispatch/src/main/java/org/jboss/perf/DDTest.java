package org.jboss.perf;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

public class DDTest {

    @org.openjdk.jmh.annotations.State(Scope.Benchmark)
    public static class State {

        @Param(value = { "A", "B", "C", "D", "E", "F", "G", "H" })
        public char clazz;

        // make sure the classes are loaded
        public A a = new A();
        public B b = new B();
        public C c = new C();
        public D d = new D();
        public E e = new E();
        public F f = new F();
        public G g = new G();
        public H h = new H();

        public I[] impls = new I[] { a, b, c, d, e, f, g, h };

        private I impl;
        private Visitor visitor = new Visitor();

        @Setup
        public void setup() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
            impl = impls[clazz - 'A'];
            // make sure the compiler knows about those methods
            Blackhole blackhole = new Blackhole();
            for (I i : impls) {
                blackhole.consume(i.accept(visitor));
            }
        }

        public I getImpl() {
            return impl;
        }

        public Visitor getVisitor() {
            return visitor;
        }
    }

    public static class Visitor {
        public int visitA() {
            return 1;
        }

        public int visitB() {
            return 2;
        }

        public int visitC() {
            return 3;
        }

        public int visitD() {
            return 4;
        }

        public int visitE() {
            return 5;
        }

        public int visitF() {
            return 6;
        }

        public int visitG() {
            return 7;
        }

        public int visitH() {
            return 8;
        }
    }

    public interface I {
        int accept(Visitor v);
    }

    public static class A implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitA();
        }
    }

    public static class B implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitB();
        }
    }

    public static class C implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitC();
        }
    }

    public static class D implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitD();
        }
    }

    public static class E implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitE();
        }
    }

    public static class F implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitF();
        }
    }

    public static class G implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitG();
        }
    }

    public static class H implements I {
        @Override
        public int accept(Visitor v) {
            return v.visitH();
        }
    }

    @Benchmark
    public void testDynamicDispatch(State state, Blackhole blackhole) {
        int value = state.getImpl().accept(state.getVisitor());
        blackhole.consume(value);
    }

    @Benchmark
    public void testInstanceofDispatch(State state, Blackhole blackhole) {
        int value = 0;
        I impl = state.getImpl();
        if (impl instanceof A) value = state.getVisitor().visitA();
        else if (impl instanceof B) value = state.getVisitor().visitB();
        else if (impl instanceof C) value = state.getVisitor().visitC();
        else if (impl instanceof D) value = state.getVisitor().visitD();
        else if (impl instanceof E) value = state.getVisitor().visitE();
        else if (impl instanceof F) value = state.getVisitor().visitF();
        else if (impl instanceof G) value = state.getVisitor().visitG();
        else if (impl instanceof H) value = state.getVisitor().visitH();
        blackhole.consume(value);
    }

}

