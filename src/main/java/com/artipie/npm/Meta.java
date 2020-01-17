/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.npm;

import io.reactivex.rxjava3.core.Completable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonPatchBuilder;

/**
 * The meta.json file.
 *
 * @since 0.1
 */
final class Meta {

    /**
     * The meta.json file.
     */
    private final Path json;

    /**
     * Ctor.
     *
     * @param json The meta.json file location on disk
     */
    Meta(final Path json) {
        this.json = json;
    }

    /**
     * Update the meta.json file by processing newly
     * uploaded {@code npm publish} generated json.
     *
     * @param uploaded The json
     * @return Completion or error signal.
     */
    public Completable update(final JsonObject uploaded) {
        return Completable.fromAction(
            () -> {
                final JsonObject meta = Json.createReader(
                    Files.newInputStream(this.json)
                ).readObject();
                final JsonObject versionsObject = uploaded.getJsonObject("versions");
                final Set<String> versions = versionsObject.keySet();
                final JsonPatchBuilder patch = Json.createPatchBuilder();
                for (final String version : versions) {
                    patch.add(
                        String.format("/versions/%s", version),
                        versionsObject.getJsonObject(version)
                    );
                }
                Files.write(
                    this.json,
                    patch
                        .build()
                        .apply(meta)
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
                );
            });
    }
}
