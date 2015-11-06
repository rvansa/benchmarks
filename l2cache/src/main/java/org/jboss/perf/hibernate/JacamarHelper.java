package org.jboss.perf.hibernate;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.jca.embedded.Embedded;
import org.jboss.jca.embedded.EmbeddedFactory;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.jnp.server.Main;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JacamarHelper {
    private Embedded embedded;
    private URL h2serverDataSourceURL;
    private ResourceAdapterArchive localJdbcRaa;
    private ResourceAdapterArchive xaJdbcRaa;

    public void start() throws Throwable {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        embedded = EmbeddedFactory.create();
        embedded.startup();
        xaJdbcRaa = createXaJdbcRaa();
        embedded.deploy(xaJdbcRaa);
        localJdbcRaa = createLocalJdbcRaa();
        embedded.deploy(localJdbcRaa);
        h2serverDataSourceURL = tmpCopy("h2server-ds.xml");
        embedded.deploy(h2serverDataSourceURL);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    public void stop() throws Throwable {
        Main namingServer = embedded.lookup("NamingServer", Main.class);
        ExecutorService lookupExecutor = null;
        if (namingServer != null) {
            if (namingServer.getLookupExector() instanceof ExecutorService) {
                lookupExecutor = (ExecutorService) namingServer.getLookupExector();
            }
        }
        embedded.undeploy(h2serverDataSourceURL);
        embedded.undeploy(localJdbcRaa);
        embedded.undeploy(xaJdbcRaa);
        embedded.shutdown();
        if (lookupExecutor != null) {
            lookupExecutor.shutdownNow();
            lookupExecutor.awaitTermination(1, TimeUnit.MINUTES);
        }
        embedded = null;
    }

    private static URL tmpCopy(String resource) throws IOException {
        File tmpFile = File.createTempFile("tmp.", "." + resource);
        tmpFile.deleteOnExit();
        ClassLoader classLoader = JacamarHelper.class.getClassLoader();
        Files.copy(classLoader.getResourceAsStream(resource), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tmpFile.toURI().toURL();
    }

    private static ResourceAdapterArchive createLocalJdbcRaa() {
        JavaArchive ja = ShrinkWrap.create(JavaArchive.class, UUID.randomUUID().toString() + ".jar");
        ja.addPackage("org.jboss.jca.adapters.jdbc");
        ResourceAdapterArchive raa = ShrinkWrap.create(ResourceAdapterArchive.class, "jdbc-local.rar");
        raa.addAsLibrary(ja);
        raa.addAsManifestResource("jdbc-local-ra.xml", "ra.xml");
        raa.addAsResource("jdbc.properties");
        return raa;
    }

    private static ResourceAdapterArchive createXaJdbcRaa() {
        JavaArchive ja = ShrinkWrap.create(JavaArchive.class, UUID.randomUUID().toString() + ".jar");
        ja.addPackage("org.jboss.jca.adapters.jdbc");
        ResourceAdapterArchive raa = ShrinkWrap.create(ResourceAdapterArchive.class, "jdbc-xa.rar");
        raa.addAsLibrary(ja);
        raa.addAsManifestResource("jdbc-xa-ra.xml", "ra.xml");
        raa.addAsResource("jdbc.properties");
        return raa;
    }
}
