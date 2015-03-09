package org.jboss.perf.hibernate;

import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.transaction.lookup.JBossStandaloneJTAManagerLookup;
import org.jboss.util.naming.NonSerializableFactory;

/**
 * Copy-paste from http://anonsvn.jboss.org/repos/hibernate/core/trunk/cache-infinispan/src/test/java/org/hibernate/test/cache/infinispan/tm/JBossStandaloneJtaExampleTest.java
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JtaHelper {
    private static final JBossStandaloneJTAManagerLookup lookup = new JBossStandaloneJTAManagerLookup();
    Context ctx;

    static {
        lookup.init(new ConfigurationBuilder().classLoader(JtaHelper.class.getClassLoader()).build());
    }

    public void start() throws Exception {
        ctx = createJndiContext();
        bindTransactionManager();
        bindUserTransaction();
    }

    public void stop() throws Exception {
        unbind("java:/TransactionManager", ctx);
        unbind("UserTransaction", ctx);
        ctx.close();
    }

    private Context createJndiContext() throws NamingException {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
        props.put("java.naming.factory.url.pkgs", "org.jboss.naming:org.jnp.interfaces");
        return new InitialContext(props);
    }

    private void bindTransactionManager() throws Exception {
        // as JBossTransactionManagerLookup extends JNDITransactionManagerLookup we must also register the TransactionManager
        bind("java:/TransactionManager", lookup.getTransactionManager(), lookup.getTransactionManager().getClass(), ctx);
    }

    private void bindUserTransaction() throws Exception {
        // also the UserTransaction must be registered on jndi: org.hibernate.transaction.JTATransactionFactory#getUserTransaction() requires this
        bind("UserTransaction", lookup.getUserTransaction(), lookup.getUserTransaction().getClass(), ctx);
    }

    /**
     * Helper method that binds the a non serializable object to the JNDI tree.
     *
     * @param jndiName  Name under which the object must be bound
     * @param who       Object to bind in JNDI
     * @param classType Class type under which should appear the bound object
     * @param ctx       Naming context under which we bind the object
     * @throws Exception Thrown if a naming exception occurs during binding
     */
    private void bind(String jndiName, Object who, Class classType, Context ctx) throws NamingException {
        // Ah ! This service isn't serializable, so we use a helper class
        NonSerializableFactory.bind(jndiName, who);
        Name n = ctx.getNameParser("").parse(jndiName);
        while (n.size() > 1) {
            String ctxName = n.get(0);
            try {
                ctx = (Context) ctx.lookup(ctxName);
            } catch (NameNotFoundException e) {
                System.out.println("Creating subcontext:" + ctxName);
                ctx = ctx.createSubcontext(ctxName);
            }
            n = n.getSuffix(1);
        }

        // The helper class NonSerializableFactory uses address type nns, we go on to
        // use the helper class to bind the service object in JNDI
        StringRefAddr addr = new StringRefAddr("nns", jndiName);
        Reference ref = new Reference(classType.getName(), addr, NonSerializableFactory.class.getName(), null);
        ctx.rebind(n.get(0), ref);
    }

    private void unbind(String jndiName, Context ctx) throws Exception {
        NonSerializableFactory.unbind(jndiName);
        ctx.unbind(jndiName);
    }
}
