/*
 * Copyright (c) 2019-2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.smallrye.mutiny.streams.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.junit.Test;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.streams.Engine;

/**
 * Checks the behavior of the {@link ConcatStageFactory} class, especially the thread handling.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ConcatStageFactoryTest extends StageTestBase {

    private final ConcatStageFactory factory = new ConcatStageFactory();

    @Test(expected = NullPointerException.class)
    public void testWithoutAStage() {
        factory.create(new Engine(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testWithoutEngine() {
        Graph g1 = () -> Collections.singletonList((Stage.Of) () -> Arrays.asList(1, 2, 3));
        Graph g2 = () -> Collections.singletonList((Stage.Of) () -> Arrays.asList(1, 2, 3));
        factory.create(null, new Stage.Concat() {
            @Override
            public Graph getFirst() {
                return g1;
            }

            @Override
            public Graph getSecond() {
                return g2;
            }
        });
    }

    @Test
    public void testConcatenationWhenAllEmissionAreMadeFromMain() throws ExecutionException, InterruptedException {
        PublisherBuilder<Integer> f1 = ReactiveStreams.of(1, 2, 3);
        PublisherBuilder<Integer> f2 = ReactiveStreams.of(4, 5, 6);

        String currentThreadName = Thread.currentThread().getName();
        LinkedHashSet<String> threads = new LinkedHashSet<>();
        CompletionStage<List<Integer>> list = ReactiveStreams.concat(f1, f2)
                .peek(i -> threads.add(Thread.currentThread().getName()))
                .toList().run();
        await().until(() -> list.toCompletableFuture().isDone());

        List<Integer> ints = list.toCompletableFuture().get();
        assertThat(ints).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(threads).hasSize(1).contains(currentThreadName);
    }

    @Test
    public void testConcatenationWhenAllEmissionsAreMadeFromDifferentThreads() throws ExecutionException,
            InterruptedException {

        ExecutorService io = Executors.newFixedThreadPool(10);
        ExecutorService computation = Executors.newFixedThreadPool(10);

        Multi<Integer> firstStream = Multi.createFrom().items(1, 2, 3).emitOn(io);
        Multi<Integer> secondStream = Multi.createFrom().items(4, 5, 6).emitOn(computation);

        LinkedHashSet<String> threads = new LinkedHashSet<>();
        CompletionStage<List<Integer>> list = ReactiveStreams.concat(
                ReactiveStreams.fromPublisher(firstStream),
                ReactiveStreams.fromPublisher(secondStream))
                .peek(i -> threads.add(Thread.currentThread().getName()))
                .toList().run();
        await().until(() -> list.toCompletableFuture().isDone());

        List<Integer> ints = list.toCompletableFuture().get();
        assertThat(ints).containsExactly(1, 2, 3, 4, 5, 6);
        // the number of thread depends on the dispatching, it's at least 2, but can be more.
        assertThat(threads).hasSizeGreaterThanOrEqualTo(2);

        io.shutdown();
        computation.shutdown();
    }

}
