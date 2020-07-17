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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import io.reactivex.Flowable;
import io.reactivex.processors.UnicastProcessor;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.subscription.UniSubscription;

public class UniCacheTest {

    private static void race(Runnable candidate1, Runnable candidate2, Executor s) {
        final CountDownLatch latch = new CountDownLatch(2);

        final RuntimeException[] errors = { null, null };

        List<Runnable> runnables = new ArrayList<>();
        runnables.add(candidate1);
        runnables.add(candidate2);
        Collections.shuffle(runnables);

        s.execute(() -> {
            try {
                runnables.get(0).run();
            } catch (RuntimeException ex) {
                errors[0] = ex;
            } finally {
                latch.countDown();
            }
        });

        s.execute(() -> {
            try {
                runnables.get(1).run();
            } catch (RuntimeException ex) {
                errors[0] = ex;
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("The wait timed out!");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }

        if (errors[0] != null) {
            throw errors[0];
        }

        if (errors[1] != null) {
            throw errors[1];
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatSourceCannotBeNull() {
        new UniCache<>(null);
    }

    @Test
    public void testThatImmediateValueAreCached() {
        AtomicInteger counter = new AtomicInteger();
        Uni<Integer> cache = Uni.createFrom().item(counter.incrementAndGet()).cache();

        UniAssertSubscriber<Integer> sub1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub2 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub3 = UniAssertSubscriber.create();

        cache.subscribe().withSubscriber(sub1);
        cache.subscribe().withSubscriber(sub2);
        cache.subscribe().withSubscriber(sub3);

        sub1.assertCompletedSuccessfully().assertItem(1);
        sub2.assertCompletedSuccessfully().assertItem(1);
        sub3.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testThatIFailureAreCached() {
        AtomicInteger counter = new AtomicInteger();
        Uni<Object> cache = Uni.createFrom().failure(new Exception("" + counter.getAndIncrement())).cache();

        UniAssertSubscriber<Object> sub1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Object> sub2 = UniAssertSubscriber.create();
        UniAssertSubscriber<Object> sub3 = UniAssertSubscriber.create();

        cache.subscribe().withSubscriber(sub1);
        cache.subscribe().withSubscriber(sub2);
        cache.subscribe().withSubscriber(sub3);

        sub1.assertFailure(Exception.class, "0");
        sub2.assertFailure(Exception.class, "0");
        sub3.assertFailure(Exception.class, "0");
    }

    @Test
    public void testThatValueEmittedAfterSubscriptionAreCached() {
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> cache = Uni.createFrom().completionStage(cs).cache();

        UniAssertSubscriber<Integer> sub1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub2 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub3 = UniAssertSubscriber.create();

        cache.subscribe().withSubscriber(sub1);
        cache.subscribe().withSubscriber(sub2);

        cs.complete(1);

        cache.subscribe().withSubscriber(sub3);

        sub1.assertCompletedSuccessfully().assertItem(1);
        sub2.assertCompletedSuccessfully().assertItem(1);
        sub3.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testThatSubscriberCanCancelTheirSubscriptionBeforeReceivingAValue() {
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> cache = Uni.createFrom().completionStage(cs).cache();

        UniAssertSubscriber<Integer> sub1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub2 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub3 = UniAssertSubscriber.create();

        cache.subscribe().withSubscriber(sub1);
        cache.subscribe().withSubscriber(sub2);

        sub2.cancel();

        cs.complete(1);

        cache.subscribe().withSubscriber(sub3);

        sub1.assertCompletedSuccessfully().assertItem(1);
        sub2.assertNotCompleted();
        sub3.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testThatSubscriberCanCancelTheirSubscriptionAfterHavingReceivingAValue() {
        CompletableFuture<Integer> cs = new CompletableFuture<>();
        Uni<Integer> cache = Uni.createFrom().completionStage(cs).cache();

        UniAssertSubscriber<Integer> sub1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub2 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> sub3 = UniAssertSubscriber.create();

        cache.subscribe().withSubscriber(sub1);
        cache.subscribe().withSubscriber(sub2);

        cs.complete(1);
        sub2.cancel();

        cache.subscribe().withSubscriber(sub3);

        sub1.assertCompletedSuccessfully().assertItem(1);
        sub2.assertCompletedSuccessfully().assertItem(1);
        sub3.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void assertCachingTheValueEmittedByAProcessor() {
        UnicastProcessor<Integer> processor = UnicastProcessor.create();
        Uni<Integer> cached = Uni.createFrom().publisher(processor).cache();

        UniAssertSubscriber<Integer> sub1 = new UniAssertSubscriber<>();
        UniAssertSubscriber<Integer> sub2 = new UniAssertSubscriber<>();

        cached.subscribe().withSubscriber(sub1);
        cached.subscribe().withSubscriber(sub2);

        sub1.assertNotCompleted();
        sub2.assertNotCompleted();

        processor.onNext(23);
        processor.onNext(42);
        processor.onComplete();

        sub1.assertCompletedSuccessfully().assertItem(23);
        sub2.assertCompletedSuccessfully().assertItem(23);
    }

    @Test
    public void assertCancellingImmediately() {
        UnicastProcessor<Integer> processor = UnicastProcessor.create();
        Uni<Integer> cached = Uni.createFrom().publisher(processor).cache();

        UniAssertSubscriber<Integer> sub1 = new UniAssertSubscriber<>(true);
        UniAssertSubscriber<Integer> sub2 = new UniAssertSubscriber<>(true);

        cached.subscribe().withSubscriber(sub1);
        cached.subscribe().withSubscriber(sub2);

        sub1.assertNoResult().assertNoFailure();
        sub2.assertNoResult().assertNoFailure();

        processor.onNext(23);
        processor.onNext(42);
        processor.onComplete();

        sub1.assertNoResult().assertNoFailure();
        sub2.assertNoResult().assertNoFailure();
    }

    @Test
    public void testSubscribersRace() {
        for (int i = 0; i < 2000; i++) {
            Flowable<Integer> flowable = Flowable.just(1, 2, 3);
            Uni<Integer> cached = Uni.createFrom().publisher(flowable).cache();

            UniAssertSubscriber<Integer> subscriber = new UniAssertSubscriber<>(false);

            Runnable r1 = () -> {
                cached.subscribe().withSubscriber(subscriber);
                subscriber.cancel();
            };

            Runnable r2 = () -> cached.subscribe().withSubscriber(new UniAssertSubscriber<>());

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            try {
                race(r1, r2, executor);
            } finally {
                executor.shutdown();
            }
        }
    }

    @Test
    public void testWithDoubleCancellation() {
        Uni<Integer> uni = Uni.createFrom().item(23).cache();
        UniSubscriber<Integer> subscriber = new UniSubscriber<Integer>() {
            @Override
            public void onSubscribe(UniSubscription subscription) {
                subscription.cancel();
                subscription.cancel();
            }

            @Override
            public void onItem(Integer ignored) {

            }

            @Override
            public void onFailure(Throwable ignored) {

            }
        };
        uni.subscribe().withSubscriber(subscriber);

        UniAssertSubscriber<Integer> test = UniAssertSubscriber.create();
        uni.subscribe().withSubscriber(test);
        test.assertCompletedSuccessfully().assertItem(23);

        uni.subscribe().withSubscriber(subscriber);
    }

}
