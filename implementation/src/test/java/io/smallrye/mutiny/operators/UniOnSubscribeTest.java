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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.testng.annotations.Test;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.smallrye.mutiny.subscription.UniSubscription;

public class UniOnSubscribeTest {

    @Test
    public void testInvoke() {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<UniSubscription> reference = new AtomicReference<>();
        Uni<Integer> uni = Uni.createFrom().item(1)
                .onSubscribe().invoke(s -> {
                    reference.set(s);
                    count.incrementAndGet();
                });

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        assertThat(count).hasValue(0);
        assertThat(reference).hasValue(null);

        uni.subscribe().withSubscriber(subscriber);

        assertThat(count).hasValue(1);
        assertThat(reference).doesNotHaveValue(null);

        uni.subscribe().withSubscriber(subscriber);

        assertThat(count).hasValue(2);
        assertThat(reference).doesNotHaveValue(null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDeprecatedOnSubscribed() {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<UniSubscription> reference = new AtomicReference<>();
        Uni<Integer> uni = Uni.createFrom().item(1)
                .on().subscribed(s -> {
                    reference.set(s);
                    count.incrementAndGet();
                });

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        assertThat(count).hasValue(0);
        assertThat(reference).hasValue(null);

        uni.subscribe().withSubscriber(subscriber);

        assertThat(count).hasValue(1);
        assertThat(reference).doesNotHaveValue(null);

        uni.subscribe().withSubscriber(subscriber);

        assertThat(count).hasValue(2);
        assertThat(reference).doesNotHaveValue(null);
    }

    @Test
    public void testInvokeUni() {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<UniSubscription> reference = new AtomicReference<>();
        AtomicReference<UniSubscription> sub = new AtomicReference<>();
        Uni<Integer> uni = Uni.createFrom().item(1)
                .onSubscribe().invokeUni(s -> {
                    reference.set(s);
                    count.incrementAndGet();
                    return Uni.createFrom().nullItem()
                            .onSubscribe().invoke(sub::set);
                });

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        assertThat(count).hasValue(0);
        assertThat(reference).hasValue(null);
        assertThat(sub).hasValue(null);

        uni.subscribe().withSubscriber(subscriber);

        assertThat(count).hasValue(1);
        assertThat(reference).doesNotHaveValue(null);
        assertThat(sub).doesNotHaveValue(null);

        uni.subscribe().withSubscriber(subscriber);

        assertThat(count).hasValue(2);
        assertThat(reference).doesNotHaveValue(null);
        assertThat(sub).doesNotHaveValue(null);

    }

    @Test
    public void testInvokeThrowingException() {
        Uni<Integer> uni = Uni.createFrom().item(1)
                .onSubscribe().invoke(s -> {
                    throw new IllegalStateException("boom");
                });

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        uni.subscribe().withSubscriber(subscriber)
                .assertFailure(IllegalStateException.class, "boom");

    }

    @Test
    public void testInvokeUniThrowingException() {
        Uni<Integer> uni = Uni.createFrom().item(1)
                .onSubscribe().invokeUni(s -> {
                    throw new IllegalStateException("boom");
                });

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        uni.subscribe().withSubscriber(subscriber)
                .assertFailure(IllegalStateException.class, "boom");

    }

    @Test
    public void testInvokeUniProvidingFailure() {
        Uni<Integer> uni = Uni.createFrom().item(1)
                .onSubscribe().invokeUni(s -> Uni.createFrom().failure(new IOException("boom")));

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        uni.subscribe().withSubscriber(subscriber)
                .assertFailure(IOException.class, "boom");

    }

    @Test
    public void testInvokeUniReturningNullUni() {
        Uni<Integer> uni = Uni.createFrom().item(1)
                .onSubscribe().invokeUni(s -> null);

        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();

        uni.subscribe().withSubscriber(subscriber)
                .assertFailure(NullPointerException.class, "`null`");

    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatInvokeConsumerCannotBeNull() {
        Uni.createFrom().item(1)
                .onSubscribe().invoke(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatInvokeUniFunctionCannotBeNull() {
        Uni.createFrom().item(1)
                .onSubscribe().invokeUni(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatInvokeUpstreamCannotBeNull() {
        new UniOnSubscribeInvoke<>(null, s -> {
        });
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatInvokeUniUpstreamCannotBeNull() {
        new UniOnSubscribeInvokeUni<>(null, s -> Uni.createFrom().nullItem());
    }

    @Test
    public void testThatSubscriptionIsNotPassedDownstreamUntilInvokeCallbackCompletes() {
        CountDownLatch latch = new CountDownLatch(1);
        UniAssertSubscriber<Integer> subscriber = Uni.createFrom().item(1)
                .onSubscribe().invoke(s -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertNotSubscribed();
        latch.countDown();
        subscriber.await()
                .assertSubscribed()
                .assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testThatSubscriptionIsNotPassedDownstreamUntilProducedUniCompletes() {
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        UniAssertSubscriber<Integer> subscriber = Uni.createFrom().item(() -> 1)
                .onSubscribe().invokeUni(s -> Uni.createFrom().emitter((Consumer<UniEmitter<? super Integer>>) emitter::set))
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertNotSubscribed();

        await().until(() -> emitter.get() != null);
        emitter.get().complete(12345);

        subscriber.await()
                .assertSubscribed()
                .assertCompletedSuccessfully().assertItem(1);

    }

    @Test
    public void testThatSubscriptionIsNotPassedDownstreamUntilProducedUniCompletesWithDifferentThread() {
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        UniAssertSubscriber<Integer> subscriber = Uni.createFrom().item(() -> 1)
                .onSubscribe().invokeUni(s -> Uni.createFrom().emitter((Consumer<UniEmitter<? super Integer>>) emitter::set))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertNotSubscribed();

        await().until(() -> emitter.get() != null);
        emitter.get().complete(12345);

        subscriber.await()
                .assertSubscribed()
                .assertCompletedSuccessfully().assertItem(1);

    }
}
