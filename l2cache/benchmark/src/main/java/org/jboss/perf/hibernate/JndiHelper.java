package org.jboss.perf.hibernate;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.jnp.interfaces.NamingContext;
import org.jnp.server.Main;
import org.jnp.server.NamingServer;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JndiHelper {
    private Main jndiServer;

    public void start() throws Exception {
        // Create an in-memory jndi
        NamingServer namingServer = new NamingServer();
        NamingContext.setLocal(namingServer);
        Main namingMain = new Main();
        namingMain.setInstallGlobalService(true);
        namingMain.setPort(-1);
        namingMain.start();
        jndiServer = namingMain;
    }

    public void stop() throws InterruptedException {
        Executor lookupExecutor = jndiServer.getLookupExector();
        jndiServer.stop();
        if (lookupExecutor instanceof ExecutorService) {
            ((ExecutorService) lookupExecutor).shutdownNow();
            ((ExecutorService) lookupExecutor).awaitTermination(1, TimeUnit.MINUTES);
        }
    }

}
