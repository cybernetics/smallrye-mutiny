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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Publisher;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import io.reactivex.Flowable;
import io.reactivex.subscribers.TestSubscriber;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public class UniToPublisherTest {

    private ExecutorService executor;

    @AfterTest
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    public void testWithImmediateValue() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        int first = Flowable.fromPublisher(publisher).blockingFirst();
        assertThat(first).isEqualTo(1);
    }

    @Test
    public void testWithImmediateNullValue() {
        Publisher<Integer> publisher = Uni.createFrom().item((Integer) null).convert().toPublisher();
        assertThat(publisher).isNotNull();
        int first = Flowable.fromPublisher(publisher).blockingFirst(2);
        assertThat(first).isEqualTo(2);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testWithImmediateFailure() {
        Publisher<Integer> publisher = Uni.createFrom().<Integer> failure(new IOException("boom")).convert().toPublisher();
        assertThat(publisher).isNotNull();
        try {
            Flowable.fromPublisher(publisher).blockingFirst();
            fail("exception expected");
        } catch (Exception e) {
            assertThat(e).hasCauseInstanceOf(IOException.class).hasMessageContaining("boom");
        }

    }

    @Test
    public void testWithImmediateValueWithRequest() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(0);
        test.assertSubscribed();
        test.request(1);
        test.assertResult(1);
        test.assertComplete();
    }

    @Test
    public void testWithImmediateValueWithRequests() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(0);
        test.assertSubscribed();
        test.request(20);
        test.assertResult(1);
        test.assertComplete();
    }

    @Test
    public void testInvalidRequest() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(0);
        test.assertSubscribed();
        test.request(0);
        test.assertError(IllegalArgumentException.class);
        test.assertTerminated();
    }

    @Test
    public void testWithImmediateValueWithOneRequestAndImmediateCancellation() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(1, true);
        test.assertSubscribed();
        assertThat(test.isCancelled()).isTrue();
        test.assertNotTerminated();
        test.assertNever(1);
    }

    @Test
    public void testThatTwoSubscribersHaveTwoSubscriptions() {
        AtomicInteger count = new AtomicInteger(1);
        Publisher<Integer> publisher = Uni.createFrom().deferred(() -> Uni.createFrom()
                .item(count.getAndIncrement()))
                .convert().toPublisher();
        assertThat(publisher).isNotNull();
        Flowable<Integer> flow = Flowable.fromPublisher(publisher);
        int first = flow.blockingFirst();
        assertThat(first).isEqualTo(1);
        first = flow.blockingFirst();
        assertThat(first).isEqualTo(2);
    }

    @Test
    public void testThatTwoSubscribersWithCache() {
        AtomicInteger count = new AtomicInteger(1);
        Publisher<Integer> publisher = Uni.createFrom()
                .deferred(() -> Uni.createFrom().item(count.getAndIncrement())).cache().convert().toPublisher();
        assertThat(publisher).isNotNull();
        Flowable<Integer> flow = Flowable.fromPublisher(publisher);
        int first = flow.blockingFirst();
        assertThat(first).isEqualTo(1);
        first = flow.blockingFirst();
        assertThat(first).isEqualTo(1);
    }

    @Test
    public void testCancellationBetweenSubscriptionAndRequest() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(0);
        test.assertSubscribed();
        test.cancel();
        assertThat(test.isCancelled()).isTrue();
        test.assertNotTerminated();
        test.assertNever(1);
    }

    @Test
    public void testCancellationBetweenRequestAndValue() {
        // TODO This is a very broken implementation of "delay" - to be replace once delay is implemented
        executor = Executors.newSingleThreadExecutor();
        Publisher<Integer> publisher = Uni.createFrom().item(1).emitOn(executor).map(x -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return x;
        }).convert().toPublisher();

        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(0);
        test.assertSubscribed();
        test.request(1);
        test.cancel();
        assertThat(test.isCancelled()).isTrue();
        test.assertNotTerminated();
        test.assertNever(1);
    }

    @Test
    public void testCancellationAfterValue() {
        Publisher<Integer> publisher = Uni.createFrom().item(1).convert().toPublisher();
        assertThat(publisher).isNotNull();
        TestSubscriber<Integer> test = Flowable.fromPublisher(publisher).test(0);
        test.assertSubscribed();
        test.request(1);
        // Immediate emission, so cancel is called after the emission.
        test.cancel();
        assertThat(test.isCancelled()).isTrue();
        test.assertValue(1);
        test.assertComplete();
    }

    @Test
    public void testWithAsyncValue() {
        executor = Executors.newSingleThreadScheduledExecutor();
        Publisher<Integer> publisher = Uni.createFrom().item(1).emitOn(executor).convert().toPublisher();
        assertThat(publisher).isNotNull();
        int first = Flowable.fromPublisher(publisher).blockingFirst();
        assertThat(first).isEqualTo(1);
    }

    @Test
    public void testWithAsyncNullValue() {
        executor = Executors.newSingleThreadScheduledExecutor();

        Publisher<Integer> publisher = Uni.createFrom().item((Integer) null)
                .emitOn(executor)
                .convert().toPublisher();
        assertThat(publisher).isNotNull();
        int first = Flowable.fromPublisher(publisher).blockingFirst(2);
        assertThat(first).isEqualTo(2);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testWithAsyncFailure() {
        executor = Executors.newSingleThreadScheduledExecutor();
        Publisher<Integer> publisher = Uni.createFrom().<Integer> failure(new IOException("boom"))
                .emitOn(executor).convert().toPublisher();
        assertThat(publisher).isNotNull();
        try {
            Flowable.fromPublisher(publisher).blockingFirst();
            fail("exception expected");
        } catch (Exception e) {
            assertThat(e).hasCauseInstanceOf(IOException.class).hasMessageContaining("boom");
        }
    }

    @Test
    public void testUniOfVoid() {
        Uni<Void> uni = Uni.createFrom().voidItem();
        Multi<Void> publisher = uni.toMulti();
        assertThat(publisher.collectItems().asList().await().indefinitely()).isEmpty();
    }
}
