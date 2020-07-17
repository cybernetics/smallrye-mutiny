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
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.MultiRetryWhenOp;
import io.smallrye.mutiny.test.MultiAssertSubscriber;
import io.smallrye.mutiny.tuples.Tuple2;

public class MultiOnFailureRetryWhenTest {

    private AtomicInteger numberOfSubscriptions;
    private Multi<Integer> failingAfter2;
    private Multi<Integer> failingAfter1;

    @BeforeMethod
    public void init() {
        numberOfSubscriptions = new AtomicInteger();
        failingAfter2 = Multi.createFrom()
                .<Integer> emitter(emitter -> emitter.emit(1).emit(2).fail(new IOException("boom")))
                .onSubscribe().invoke(s -> numberOfSubscriptions.incrementAndGet());

        failingAfter1 = Multi.createBy().concatenating()
                .streams(Multi.createFrom().item(1), Multi.createFrom().failure(new RuntimeException("boom")));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatUpstreamCannotBeNull() {
        new MultiRetryWhenOp<>(null, v -> v);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatStreamFactoryCannotBeNull() {
        Multi.createFrom().nothing().onFailure().retry().when(null);
    }

    @Test
    public void testThatCancellingTheStreamCancelTheProducedWhenStream() {
        AtomicInteger cancelled = new AtomicInteger(0);
        Multi<Integer> when = Multi.createFrom().range(1, 10)
                .on().cancellation(cancelled::incrementAndGet);

        MultiAssertSubscriber<Integer> subscriber = failingAfter1
                .onFailure().retry().when(x -> when)
                .subscribe().withSubscriber(MultiAssertSubscriber.create(3));

        subscriber.assertReceived(1, 1, 1)
                .cancel();

        assertThat(cancelled).hasValue(1);

        subscriber.cancel();
        assertThat(cancelled).hasValue(1);
    }

    @Test
    public void testWithOtherStreamFailing() {
        AtomicBoolean subscribed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        Multi<Integer> multi = failingAfter1
                .onSubscribe().invoke(sub -> subscribed.set(true))
                .on().cancellation(() -> cancelled.set(true));

        MultiAssertSubscriber<Integer> subscriber = multi
                .onFailure().retry().when(other -> Multi.createFrom().failure(new IllegalStateException("boom")))
                .subscribe().withSubscriber(MultiAssertSubscriber.create(10));

        subscriber
                .assertSubscribed()
                .assertHasFailedWith(IllegalStateException.class, "boom");

        assertThat(subscribed.get()).isFalse();
        assertThat(cancelled.get()).isFalse();
    }

    @Test
    @Ignore("To be investigated - the switch to the strict serializer broken the cancellation check")
    public void testWhatTheWhenStreamFailsTheUpstreamIsCancelled() {
        AtomicBoolean subscribed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        Multi<Integer> multi = failingAfter1
                .onSubscribe().invoke(sub -> subscribed.set(true))
                .on().cancellation(() -> cancelled.set(true));

        AtomicInteger count = new AtomicInteger();
        Multi<Integer> retry = multi
                .onFailure().retry()
                .when(other -> other.flatMap(l -> count.getAndIncrement() == 0 ? Multi.createFrom().item(l)
                        : Multi.createFrom().failure(new IllegalStateException("boom"))));

        MultiAssertSubscriber<Integer> subscriber = retry.subscribe()
                .withSubscriber(MultiAssertSubscriber.create(10));
        subscriber
                .assertSubscribed()
                .assertReceived(1, 1)
                .assertHasFailedWith(IllegalStateException.class, "boom");

        assertThat(subscribed.get()).isTrue();
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    public void testCompletionWhenOtherStreamCompletes() {
        AtomicBoolean subscribed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        Multi<Integer> source = failingAfter1
                .onSubscribe().invoke(sub -> subscribed.set(true))
                .on().cancellation(() -> cancelled.set(true));

        Multi<Integer> retry = source
                .onFailure().retry().when(other -> Multi.createFrom().empty());

        MultiAssertSubscriber<Integer> subscriber = retry.subscribe()
                .withSubscriber(MultiAssertSubscriber.create(10));
        subscriber
                .assertSubscribed()
                .assertCompletedSuccessfully();

        assertThat(subscribed.get()).isFalse();
        assertThat(cancelled.get()).isFalse();
    }

    @Test
    public void testAfterOnRetryAndCompletion() {
        AtomicBoolean sourceSubscribed = new AtomicBoolean();
        Multi<Integer> source = failingAfter1
                .onSubscribe().invoke(sub -> sourceSubscribed.set(true));

        Multi<Integer> retry = source
                .onFailure().retry().when(other -> other
                        .transform().byTakingFirstItems(1));

        MultiAssertSubscriber<Integer> subscriber = retry.subscribe()
                .withSubscriber(MultiAssertSubscriber.create(10));
        subscriber
                .assertSubscribed()
                .assertReceived(1, 1)
                .assertCompletedSuccessfully();
        assertThat(sourceSubscribed.get()).isTrue();
    }

    @Test
    public void testRepeat() {
        MultiAssertSubscriber<Integer> subscriber = MultiAssertSubscriber.create(11);

        failingAfter1
                .onFailure().retry().when(v -> Multi.createFrom().range(1, 11))
                .subscribe(subscriber);

        subscriber
                .assertReceived(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
                .assertCompletedSuccessfully();
    }

    @Test
    public void testRepeatWithBackPressure() {
        MultiAssertSubscriber<Integer> subscriber = MultiAssertSubscriber.create(0);

        failingAfter2
                .onFailure().retry().when(v -> Multi.createFrom().range(1, 6))
                .subscribe(subscriber);

        subscriber
                .assertHasNotReceivedAnyItem()
                .assertNotTerminated()

                .request(1)
                .assertReceived(1)
                .assertNotTerminated()

                .request(2)
                .assertReceived(1, 2, 1)
                .assertNotTerminated()

                .request(5)
                .assertReceived(1, 2, 1, 2, 1, 2, 1, 2)
                .assertNotTerminated()

                .request(10)
                .assertReceived(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
                .assertCompletedSuccessfully();
    }

    @Test
    public void testWithOtherStreamBeingEmpty() {
        MultiAssertSubscriber<Integer> subscriber = MultiAssertSubscriber.create(0);

        failingAfter2
                .onFailure().retry().when(v -> Multi.createFrom().empty())
                .subscribe(subscriber);

        subscriber
                .assertHasNotReceivedAnyItem()
                .assertCompletedSuccessfully();
    }

    @Test
    public void testWithOtherStreamFailing2() {
        MultiAssertSubscriber<Integer> subscriber = MultiAssertSubscriber.create(0);

        failingAfter2
                .onFailure().retry().when(v -> Multi.createFrom().failure(new RuntimeException("failed")))
                .subscribe(subscriber);

        subscriber
                .assertHasNotReceivedAnyItem()
                .assertHasFailedWith(RuntimeException.class, "failed");
    }

    @Test
    public void testWithOtherStreamThrowingException() {
        MultiAssertSubscriber<Integer> subscriber = MultiAssertSubscriber.create(10);

        failingAfter2
                .onFailure().retry().when(v -> {
                    throw new RuntimeException("failed");
                })
                .subscribe(subscriber);

        subscriber
                .assertHasNotReceivedAnyItem()
                .assertHasFailedWith(RuntimeException.class, "failed");

    }

    @Test
    public void testWithOtherStreamReturningNull() {
        MultiAssertSubscriber<Integer> subscriber = MultiAssertSubscriber.create(10);

        failingAfter2
                .onFailure().retry().when(v -> null)
                .subscribe(subscriber);

        subscriber
                .assertHasNotReceivedAnyItem()
                .assertHasFailedWith(NullPointerException.class, "");

    }

    @Test
    public void testWithOtherStreamFailingAfterAFewItems() {
        MultiAssertSubscriber<Integer> ts = MultiAssertSubscriber.create(10);

        failingAfter2.onFailure().retry().when(v -> v.map(a -> {
            throw new RuntimeException("failed");
        }))
                .subscribe(ts);

        ts.assertReceived(1, 2)
                .assertHasFailedWith(RuntimeException.class, "failed");

    }

    @Test
    public void testInfiniteRetry() {
        MultiAssertSubscriber<Integer> ts = MultiAssertSubscriber.create(0);

        failingAfter2
                .onFailure().retry().when(v -> v)
                .subscribe(ts);

        ts.request(8);

        ts.assertReceived(1, 2, 1, 2, 1, 2, 1, 2)
                .assertNotTerminated();

        ts.cancel();
    }

    Multi<String> getMultiWithManualExponentialRetry() {
        AtomicInteger i = new AtomicInteger();
        return Multi.createFrom().<String> emitter(s -> {
            if (i.incrementAndGet() == 4) {
                s.emit("hey");
            } else {
                s.fail(new RuntimeException("test " + i));
            }
        }).onFailure().retry().when(
                repeat -> {
                    return Multi.createBy().combining().streams(repeat, Multi.createFrom().range(1, 4)).asTuple()
                            .map(Tuple2::getItem2)
                            .onItem().transformToUni(time -> Uni.createFrom().item(time)
                                    .onItem().delayIt().by(Duration.ofMillis(time)))
                            .concatenate();
                });
    }

    @Test
    public void testManualExponentialRetry() {
        MultiAssertSubscriber<String> subscriber = getMultiWithManualExponentialRetry()
                .subscribe().withSubscriber(MultiAssertSubscriber.create(100));

        subscriber
                .await(Duration.ofSeconds(10))
                .assertReceived("hey")
                .assertCompletedSuccessfully();
    }

    @Test
    public void testRetryWithRandomBackoff() {
        Exception exception = new IOException("boom retry");

        MultiAssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(e -> {
            e.emit(0);
            e.emit(1);
            e.fail(exception);
        })
                .onFailure().retry().withBackOff(Duration.ofMillis(10), Duration.ofHours(1)).withJitter(0.1)
                .atMost(4)
                .subscribe().withSubscriber(MultiAssertSubscriber.create(100));
        await().until(() -> subscriber.items().size() >= 8);
        subscriber
                .assertReceived(0, 1, 0, 1, 0, 1, 0, 1)
                .assertHasFailedWith(IllegalStateException.class, "Retries exhausted");

    }

    @Test
    public void testRetryWithRandomBackoffAndDefaultJitter() {
        Exception exception = new IOException("boom retry");
        MultiAssertSubscriber<Integer> subscriber = Multi.createBy().concatenating()
                .streams(Multi.createFrom().range(0, 2), Multi.createFrom().failure(exception))

                .onFailure().retry().withBackOff(Duration.ofMillis(100), Duration.ofHours(1))
                .atMost(4)
                .subscribe().withSubscriber(MultiAssertSubscriber.create(100));

        await().until(() -> subscriber.items().size() >= 8);
        subscriber
                .assertReceived(0, 1, 0, 1, 0, 1, 0, 1)
                .assertHasFailedWith(IllegalStateException.class, "Retries exhausted: 4/4");
    }

    @Test
    public void testRetryWithDefaultMax() {
        Exception exception = new IOException("boom retry");
        MultiAssertSubscriber<Integer> subscriber = Multi.createBy().concatenating()
                .streams(Multi.createFrom().range(0, 2), Multi.createFrom().failure(exception))
                .onFailure().retry().withBackOff(Duration.ofMillis(10)).withJitter(0.0)
                .atMost(4)
                .subscribe().withSubscriber(MultiAssertSubscriber.create(100));

        await()
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> subscriber.items().size() >= 8);
        subscriber
                .assertReceived(0, 1, 0, 1, 0, 1, 0, 1)
                .assertHasFailedWith(IllegalStateException.class, "Retries exhausted: 4/4");
    }

}
