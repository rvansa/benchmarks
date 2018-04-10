package org.jboss.perf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Main {
    public static void main(String[] args) throws IOException {
        final MarshallerFactory marshallerFactory = Marshalling.getProvidedMarshallerFactory("river");

        // Create a configuration
        final MarshallingConfiguration configuration = new MarshallingConfiguration();
        // Use version 3
        configuration.setVersion(3);
        System.out.printf("A(null): %d\n", computeSize(marshallerFactory, configuration, new A(null)));
        System.out.printf("A(''): %d\n", computeSize(marshallerFactory, configuration, new A("")));
        System.out.printf("A('f'): %d\n", computeSize(marshallerFactory, configuration, new A("f")));
        System.out.printf("A('foo'): %d\n", computeSize(marshallerFactory, configuration, new A("foo")));
        System.out.printf("B(null, null): %d\n", computeSize(marshallerFactory, configuration, new B(null, null)));
        System.out.printf("B(null, 'b'): %d\n", computeSize(marshallerFactory, configuration, new B(null, "b")));
        System.out.printf("B('f', null): %d\n", computeSize(marshallerFactory, configuration, new B("f", null)));
        System.out.printf("B('foo', 'bar'): %d\n", computeSize(marshallerFactory, configuration, new B("f", "b")));
        System.out.printf("C(null, null): %d\n", computeSize(marshallerFactory, configuration, new C(null, null)));
        System.out.printf("C(null, 'b'): %d\n", computeSize(marshallerFactory, configuration, new C(null, "b")));
        System.out.printf("C('f', null): %d\n", computeSize(marshallerFactory, configuration, new C("f", null)));
        System.out.printf("C('foo', 'bar'): %d\n", computeSize(marshallerFactory, configuration, new C("f", "b")));
        System.out.printf("D(null): %d\n", computeSize(marshallerFactory, configuration, new D(null)));
        System.out.printf("D('f'): %d\n", computeSize(marshallerFactory, configuration, new D("f")));
        System.out.printf("ClassWith25CharactersName('f'): %d\n", computeSize(marshallerFactory, configuration, new ClassWith25CharactersName("f")));
        System.out.printf("2x A(null): %d\n", computeSize(marshallerFactory, configuration, new A(null), new A(null)));
        System.out.printf("2x A('f'): %d\n", computeSize(marshallerFactory, configuration, new A("f"), new A("b")));
        System.out.printf("2x B(null, null): %d\n", computeSize(marshallerFactory, configuration, new B(null, null), new B(null, null)));
        System.out.printf("2x B('f', 'b'): %d\n", computeSize(marshallerFactory, configuration, new B("f", "b"), new B("x", "y")));
        System.out.printf("2x C(null, null): %d\n", computeSize(marshallerFactory, configuration, new C(null, null), new C(null, null)));
        System.out.printf("2x C('f', 'b'): %d\n", computeSize(marshallerFactory, configuration, new C("f", "b"), new C("x", "y")));
        System.out.printf("2x D('f'): %d\n", computeSize(marshallerFactory, configuration, new D("f"), new D("b")));
        System.out.printf("2x ClassWith25CharactersName('f'): %d\n", computeSize(marshallerFactory, configuration, new ClassWith25CharactersName("f"), new ClassWith25CharactersName("b")));
    }

    private static int computeSize(MarshallerFactory marshallerFactory, MarshallingConfiguration configuration, Object... objects) throws IOException {
        final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        marshaller.start(Marshalling.createByteOutput(os));
        for (Object o : objects) {
            marshaller.writeObject(o);
        }
        marshaller.finish();
        os.close();
        return os.size();
    }

    public static class A implements Serializable {
        String foo;

        private A() {}

        public A(String foo) {
            this.foo = foo;
        }
    }

    public static class B extends A {
        String bar;

        private B() {}

        public B(String foo, String bar) {
            super(foo);
            this.bar = bar;
        }
    }

    public static class C implements Serializable {
        String foo;
        String bar;

        private C() {}

        public C(String foo, String bar) {
            this.foo = foo;
            this.bar = bar;
        }
    }

    public static class ClassWith25CharactersName implements Serializable {
        String foo;

        public ClassWith25CharactersName(String foo) {
            this.foo = foo;
        }
    }

    public static class D implements Serializable {
        String variableWith28CharactersName;

        public D(String variableWith28CharactersName) {
            this.variableWith28CharactersName = variableWith28CharactersName;
        }
    }
}
