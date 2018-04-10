package org.jboss.perf;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Collections;

public class DDTest {

    @org.openjdk.jmh.annotations.State(Scope.Benchmark)
    public static class State {

//        @Param(value = "10")
//        public int aRatio;


        public A a = new A();
        public B b = new B();
        public C c = new C();
        public D d = new D();
        public E e = new E();
        public F f = new F();
        public G g = new G();
        public H h = new H();

        public I[] impls;

        private Visitor visitor = new Visitor();

        @Setup
        public void setup() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
            ArrayList<I> clazzes = new ArrayList<I>();
            clazzes.add(a);
            clazzes.add(b);
            clazzes.add(c);
            clazzes.add(d);
            clazzes.add(e);
            clazzes.add(f);
            clazzes.add(g);
            clazzes.add(h);
            Collections.shuffle(clazzes);
            impls = clazzes.toArray(new I[clazzes.size()]);
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

    public static abstract class I {
        public final int commandId;

        protected I(int commandId) {
            this.commandId = commandId;
        }

        public abstract int accept(Visitor v);
    }

    public static class A extends I {
        public A() {
            super(1);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitA();
        }
    }

    public static class B extends I {
        public B() {
            super(2);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitB();
        }
    }

    public static class C extends I {
        public C() {
            super(3);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitC();
        }
    }

    public static class D extends I {
        public D() {
            super(4);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitD();
        }
    }

    public static class E extends I {
        public E() {
            super(5);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitE();
        }
    }

    public static class F extends I {
        public F() {
            super(6);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitF();
        }
    }

    public static class G extends I {
        public G() {
            super(7);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitG();
        }
    }

    public static class H extends I {
        public H() {
            super(8);
        }

        @Override
        public int accept(Visitor v) {
            return v.visitH();
        }
    }

    @Benchmark
    public void testDynamicDispatch(State state, Blackhole blackhole) {
        for (I impl : state.impls) {
            int value = impl.accept(state.getVisitor());
            blackhole.consume(value);
        }
    }

    @Benchmark
    public void testInstanceofDispatch(State state, Blackhole blackhole) {
        for (I impl : state.impls) {
            int value = 0;
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

    @Benchmark
    public void testSwitchDispatch(State state, Blackhole blackhole) {
        for (I impl : state.impls) {
            int value = 0;
            switch (impl.commandId) {
                case 1: value = state.getVisitor().visitA(); break;
                case 2: value = state.getVisitor().visitB(); break;
                case 3: value = state.getVisitor().visitC(); break;
                case 4: value = state.getVisitor().visitD(); break;
                case 5: value = state.getVisitor().visitE(); break;
                case 6: value = state.getVisitor().visitF(); break;
                case 7: value = state.getVisitor().visitG(); break;
                case 8: value = state.getVisitor().visitH(); break;
            }
            blackhole.consume(value);
        }
    }

}

