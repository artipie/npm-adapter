/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.npm.proxy;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Http NPM Remote client test.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.TooManyMethods"})
public final class HttpNpmRemoteTest {
    /**
     * Last modified date for both package and asset.
     */
    private static final String LAST_MODIFIED = "Tue, 24 Mar 2020 12:15:16 GMT";

    /**
     * Asset Content-Type.
     */
    private static final String DEF_CONTENT_TYPE = "application/octet-stream";

    /**
     * Assert content.
     */
    private static final String DEF_CONTENT = "foobar";

    /**
     * Vertx instance.
     */
    private static Vertx vertx;

    /**
     * NPM Remote client instance.
     */
    private HttpNpmRemote remote;

    /**
     * Http Server instance.
     */
    private HttpServer server;

    @Test
    public void loadsPackage() throws IOException, JSONException {
        final String name = "asdas";
        final OffsetDateTime started = OffsetDateTime.now();
        final NpmPackage pkg = this.remote.loadPackage(name).blockingGet();
        MatcherAssert.assertThat("Package is null", pkg != null);
        MatcherAssert.assertThat(
            "Package name is correct",
            pkg.name(),
            new IsEqual<>(name)
        );
        JSONAssert.assertEquals(
            IOUtils.resourceToString("/json/cached.json", StandardCharsets.UTF_8),
            pkg.content(),
            true
        );
        MatcherAssert.assertThat(
            "Metadata last modified date is correct",
            pkg.meta().lastModified(),
            new IsEqual<>(HttpNpmRemoteTest.LAST_MODIFIED)
        );
        final OffsetDateTime checked = OffsetDateTime.now();
        MatcherAssert.assertThat(
            String.format(
                "Unexpected last refreshed date: %s (started: %s, checked: %s)",
                    pkg.meta().lastRefreshed(),
                started,
                checked
            ),
            pkg.meta().lastRefreshed().isAfter(started)
                && !pkg.meta().lastRefreshed().isAfter(checked)
        );
    }

    @Test
    public void loadsAsset() throws IOException {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        final Path tmp = Files.createTempFile("npm-asset-", "tmp");
        try {
            final NpmAsset asset = this.remote.loadAsset(path, tmp).blockingGet();
            MatcherAssert.assertThat("Asset is null", asset != null);
            MatcherAssert.assertThat(
                "Path to asset is correct",
                asset.path(),
                new IsEqual<>(path)
            );
            MatcherAssert.assertThat(
                "Content of asset is correct",
                new PublisherAs(asset.dataPublisher())
                    .asciiString()
                    .toCompletableFuture().join(),
                new IsEqual<>(HttpNpmRemoteTest.DEF_CONTENT)
            );
            MatcherAssert.assertThat(
                "Modified date is correct",
                asset.meta().lastModified(),
                new IsEqual<>(HttpNpmRemoteTest.LAST_MODIFIED)
            );
            MatcherAssert.assertThat(
                "Content-type of asset is correct",
                asset.meta().contentType(),
                new IsEqual<>(HttpNpmRemoteTest.DEF_CONTENT_TYPE)
            );
        } finally {
            Files.delete(tmp);
        }
    }

    @Test
    public void doesNotFindPackage() {
        final Boolean empty = this.remote.loadPackage("not-found").isEmpty().blockingGet();
        MatcherAssert.assertThat("Unexpected package found", empty);
    }

    @Test
    public void doesNotFindAsset() throws IOException {
        final Path tmp = Files.createTempFile("npm-asset-", "tmp");
        try {
            final Boolean empty = this.remote.loadAsset("not-found", tmp)
                .isEmpty().blockingGet();
            MatcherAssert.assertThat("Unexpected asset found", empty);
        } finally {
            Files.delete(tmp);
        }
    }

    @Test
    public void failsToLoadPackage() throws InterruptedException {
        this.stopServer();
        final Boolean empty = this.remote.loadPackage("asdas").isEmpty().blockingGet();
        MatcherAssert.assertThat("Unexpected package found", empty);
    }

    @Test
    public void failsToLoadAsset() throws InterruptedException, IOException {
        this.stopServer();
        final Path tmp = Files.createTempFile("npm-asset-", "tmp");
        try {
            final Boolean empty = this.remote.loadAsset("asdas/-/asdas-1.0.0.tgz", tmp)
                .isEmpty().blockingGet();
            MatcherAssert.assertThat("Unexpected asset found", empty);
        } finally {
            Files.delete(tmp);
        }
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        final int port = this.rndPort();
        this.server = HttpNpmRemoteTest.prepareServer(port);
        final YamlMapping yaml = Yaml.createYamlMappingBuilder()
            .add(
                "remote",
                Yaml.createYamlMappingBuilder().add(
                    "url",
                        String.format("http://localhost:%d", port)
                ).build()
            ).build();
        this.remote = new HttpNpmRemote(new NpmProxyConfig(yaml), HttpNpmRemoteTest.vertx);
    }

    @AfterEach
    void tearDown() {
        this.remote.close();
        this.server.close();
    }

    @BeforeAll
    static void prepare() {
        HttpNpmRemoteTest.vertx = Vertx.vertx();
    }

    @AfterAll
    static void cleanup() {
        HttpNpmRemoteTest.vertx.close();
    }

    private int rndPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpServer prepareServer(final int port)
        throws IOException, InterruptedException {
        final String original = IOUtils.resourceToString(
            "/json/original.json",
            StandardCharsets.UTF_8
        );
        final CountDownLatch latch = new CountDownLatch(1);
        final HttpServer server = vertx.createHttpServer().requestHandler(
            req -> {
                if (req.path().equalsIgnoreCase("/asdas")) {
                    req.response()
                        .putHeader("Last-Modified", HttpNpmRemoteTest.LAST_MODIFIED)
                        .end(original);
                } else if (req.path().equalsIgnoreCase("/asdas/-/asdas-1.0.0.tgz")) {
                    req.response()
                        .putHeader("Last-Modified", HttpNpmRemoteTest.LAST_MODIFIED)
                        .putHeader("Content-Type", HttpNpmRemoteTest.DEF_CONTENT_TYPE)
                        .end(HttpNpmRemoteTest.DEF_CONTENT);
                } else {
                    // @checkstyle MagicNumberCheck (1 line)
                    req.response().setStatusCode(404).end();
                }
            }
        ).listen(port, unused -> latch.countDown());
        latch.await();
        return server;
    }

    private void stopServer() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        this.server.close(unused -> latch.countDown());
        latch.await();
    }
}
