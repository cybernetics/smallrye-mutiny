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
package io.smallrye.mutiny.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.annotations.Test;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.test.MultiAssertSubscriber;

public class StrictMultiSubscriberTest {

    @Test
    public void testItemReception() {
        MultiAssertSubscriber<Integer> test = MultiAssertSubscriber.create(Long.MAX_VALUE);
        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(test);
        Multi.createFrom().range(0, 5)
                .subscribe().withSubscriber(strict);
        test.assertReceived(0, 1, 2, 3, 4).assertCompletedSuccessfully();
    }

    @Test
    public void testItemReceptionWithBackPressure() {
        MultiAssertSubscriber<Integer> test = MultiAssertSubscriber.create(0);
        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(test);
        Multi.createFrom().range(0, 5)
                .subscribe().withSubscriber(strict);

        test.assertNotTerminated().assertHasNotReceivedAnyItem();

        test.request(2);
        test.assertReceived(0, 1).assertNotTerminated();

        test.request(3);
        test.assertReceived(0, 1, 2, 3, 4).assertCompletedSuccessfully();

        test.request(2);
        test.assertReceived(0, 1, 2, 3, 4).assertCompletedSuccessfully();
    }

    @Test
    public void testFailureReception() {
        MultiAssertSubscriber<Integer> test = MultiAssertSubscriber.create(Long.MAX_VALUE);
        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(test);
        Multi.createFrom().range(0, 5).onCompletion().failWith(new IOException("boom"))
                .subscribe().withSubscriber(strict);
        test.assertReceived(0, 1, 2, 3, 4)
                .assertHasFailedWith(IOException.class, "boom");
    }

    @Test
    public void testThatOnRequestInSubscribeDoesNotViolatedTheProtocol() {
        // onItem must not be called before the end of onSubscribe
        AtomicInteger protection = new AtomicInteger();
        AtomicReference<Integer> item = new AtomicReference<>();
        @SuppressWarnings("ReactiveStreamsSubscriberImplementation")
        Subscriber<Integer> subscriber = new Subscriber<Integer>() {

            @Override
            public void onSubscribe(Subscription subscription) {
                if (protection.compareAndSet(0, 1)) {
                    subscription.request(1);
                } else {
                    throw new IllegalStateException(
                            "Protocol violated - onSubscribe is not called sequentially: " + protection.get());
                }
                if (!protection.compareAndSet(1, 2)) {
                    throw new IllegalStateException(
                            "Protocol violated - onSubscribe is not called sequentially: " + protection.get());
                }
            }

            @Override
            public void onNext(Integer integer) {
                if (protection.compareAndSet(2, 3)) {
                    item.set(integer);
                } else {
                    throw new IllegalStateException(
                            "Protocol violated - onNext is not called sequentially: " + protection.get());
                }
                if (!protection.compareAndSet(3, 4)) {
                    throw new IllegalStateException(
                            "Protocol violated - onNext is not called sequentially : " + protection.get());
                }
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        };

        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(subscriber);
        Multi.createFrom().items(23).subscribe().withSubscriber(strict);

        assertThat(item).hasValue(23);
        assertThat(protection).hasValue(4);
    }

    @Test
    public void testFailureReceptionWhenRequesting0() {
        AtomicReference<Integer> item = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        @SuppressWarnings("ReactiveStreamsSubscriberImplementation")
        Subscriber<Integer> subscriber = new Subscriber<Integer>() {

            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(0);
            }

            @Override
            public void onNext(Integer integer) {
                item.set(integer);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
            }

            @Override
            public void onComplete() {

            }
        };

        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(subscriber);
        Multi.createFrom().items(23).subscribe().withSubscriber(strict);

        assertThat(item).doesNotHaveValue(23);
        assertThat(failure.get()).satisfies(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3.9"));
    }

    @Test
    public void testFailureReceptionWhenRequestingNegative() {
        AtomicReference<Integer> item = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        @SuppressWarnings("ReactiveStreamsSubscriberImplementation")
        Subscriber<Integer> subscriber = new Subscriber<Integer>() {

            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }

            @Override
            public void onNext(Integer integer) {
                item.set(integer);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
            }

            @Override
            public void onComplete() {

            }
        };

        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(subscriber);
        Multi.createFrom().items(23).subscribe().withSubscriber(strict);

        assertThat(item).doesNotHaveValue(23);
        assertThat(failure.get()).satisfies(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3.9"));
    }

    @Test
    public void testOnSubscribeCalledTwice() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        @SuppressWarnings("ReactiveStreamsSubscriberImplementation")
        Subscriber<Integer> subscriber = new Subscriber<Integer>() {

            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(2);
            }

            @Override
            public void onNext(Integer integer) {
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
            }

            @Override
            public void onComplete() {

            }
        };

        StrictMultiSubscriber<Integer> strict = new StrictMultiSubscriber<>(subscriber);

        Subscription subscription1 = mock(Subscription.class);
        Subscription subscription2 = mock(Subscription.class);
        strict.onSubscribe(subscription1);
        assertThat(failure).hasValue(null);
        strict.onSubscribe(subscription2);
        assertThat(failure.get()).satisfies(t -> assertThat(t).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2.12"));
        verify(subscription2).cancel();
        verify(subscription1).cancel();
    }
}
