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
package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import io.smallrye.mutiny.Uni;

public class UniSubscribeAsCompletionStageTest {

    private ScheduledExecutorService executor;

    @AfterTest
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    public void testWithImmediateValue() {
        CompletableFuture<Integer> future = Uni.createFrom().item(1).subscribe().asCompletionStage();
        assertThat(future).isNotNull();
        assertThat(future).isCompletedWithValue(1);
    }

    @Test
    public void testShortcut() {
        CompletableFuture<Integer> future = Uni.createFrom().item(1).subscribeAsCompletionStage();
        assertThat(future).isNotNull();
        assertThat(future).isCompletedWithValue(1);
    }

    @Test
    public void testWithImmediateVoidItem() {
        CompletableFuture<Void> future = Uni.createFrom().voidItem().subscribe().asCompletionStage();
        assertThat(future).isNotNull();
        assertThat(future).isCompletedWithValue(null);
    }

    @Test
    public void testWithImmediateNullItem() {
        CompletableFuture<String> future = Uni.createFrom().<String> nullItem().subscribe().asCompletionStage();
        assertThat(future).isNotNull();
        assertThat(future).isCompletedWithValue(null);
    }

    @Test
    public void testWithImmediateFailure() {
        CompletableFuture<Integer> future = Uni.createFrom().<Integer> failure(new IOException("boom")).subscribe()
                .asCompletionStage();
        assertThat(future).isNotNull();
        try {
            future.join();
            fail("exception expected");
        } catch (Exception e) {
            assertThat(e).hasCauseInstanceOf(IOException.class).hasMessageContaining("boom");
        }

    }

    @Test
    public void testThatSubscriptionsAreNotShared() {
        AtomicInteger count = new AtomicInteger(1);
        Uni<Integer> deferred = Uni.createFrom().deferred(() -> Uni.createFrom().item(count.getAndIncrement()));
        CompletionStage<Integer> cs1 = deferred.subscribe().asCompletionStage();
        CompletionStage<Integer> cs2 = deferred.subscribe().asCompletionStage();
        assertThat(cs1).isNotNull();
        assertThat(cs2).isNotNull();

        assertThat(cs1).isCompletedWithValue(1);
        assertThat(cs2).isCompletedWithValue(2);
    }

    @Test
    public void testThatTwoSubscribersWithCache() {
        AtomicInteger count = new AtomicInteger(1);
        Uni<Integer> cached = Uni.createFrom().deferred(() -> Uni.createFrom().item(count.getAndIncrement())).cache();
        CompletionStage<Integer> cs1 = cached.subscribe().asCompletionStage();
        CompletionStage<Integer> cs2 = cached.subscribe().asCompletionStage();
        assertThat(cs1).isNotNull();
        assertThat(cs2).isNotNull();
        assertThat(cs1).isCompletedWithValue(1);
        assertThat(cs1).isCompletedWithValue(1);
    }

    @Test
    public void testCancellationWithImmediateValue() {
        AtomicInteger value = new AtomicInteger(-1);
        CompletableFuture<Integer> future = Uni.createFrom().item(1).subscribe().asCompletionStage()
                .whenComplete((res, fail) -> value.set(res));
        future.cancel(false);
        assertThat(future).isNotCancelled(); // Too late.
        assertThat(value).hasValue(1);
    }

    @Test
    public void testCancellationWithAsyncValue() {
        executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger value = new AtomicInteger(-1);
        CompletableFuture<Integer> future = Uni.createFrom().item(1)
                .onItem().delayIt().onExecutor(executor).by(Duration.ofMillis(100))
                .emitOn(executor)
                .subscribe().asCompletionStage()
                .whenComplete((res, fail) -> value.set(res));

        future.cancel(false);
        assertThat(value).hasValue(-1);
    }

    @Test
    public void testWithAsyncValue() {
        executor = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Integer> future = Uni.createFrom().item(1)
                .emitOn(executor).subscribe().asCompletionStage();
        await().until(future::isDone);
        assertThat(future).isCompletedWithValue(1);
    }

    @Test
    public void testWithAsyncVoidItem() {
        executor = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> future = Uni.createFrom().voidItem().emitOn(executor)
                .subscribe().asCompletionStage();
        await().until(future::isDone);
        assertThat(future).isCompletedWithValue(null);
    }

    @Test
    public void testWithAsyncNullItem() {
        executor = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<String> future = Uni.createFrom().<String> nullItem().emitOn(executor)
                .subscribe().asCompletionStage();
        await().until(future::isDone);
        assertThat(future).isCompletedWithValue(null);
    }

    @Test
    public void testWithAsyncFailure() {
        executor = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Integer> future = Uni.createFrom().<Integer> failure(new IOException("boom"))
                .emitOn(executor).subscribe().asCompletionStage();
        await().until(future::isDone);
        assertThat(future).isCompletedExceptionally();
    }

}
