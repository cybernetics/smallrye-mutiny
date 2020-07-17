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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.testng.annotations.Test;

import io.smallrye.mutiny.Uni;

public class UniCreateFromItemTest {

    @Test
    public void testThatNullValueAreAccepted() {
        UniAssertSubscriber<Object> ts = UniAssertSubscriber.create();
        Uni.createFrom().item((String) null).subscribe().withSubscriber(ts);
        ts.assertCompletedSuccessfully().assertItem(null);
    }

    @Test
    public void testWithNonNullValue() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();
        Uni.createFrom().item(1).subscribe().withSubscriber(ts);
        ts.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testThatEmptyIsAcceptedWithFromOptional() {
        UniAssertSubscriber<Object> ts = UniAssertSubscriber.create();
        Uni.createFrom().optional(Optional.empty()).subscribe().withSubscriber(ts);
        ts.assertCompletedSuccessfully().assertItem(null);
    }

    @SuppressWarnings({ "OptionalAssignedToNull", "unchecked" })
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatNullIfNotAcceptedByFromOptional() {
        Uni.createFrom().optional((Optional) null); // Immediate failure, no need for subscription
    }

    @Test
    public void testThatFulfilledOptionalIsAcceptedWithFromOptional() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();
        Uni.createFrom().optional(Optional.of(1)).subscribe().withSubscriber(ts);
        ts.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testThatValueIsNotEmittedBeforeSubscription() {
        UniAssertSubscriber<Integer> ts = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni<Integer> uni = Uni.createFrom().item(1).map(i -> {
            called.set(true);
            return i + 1;
        });

        assertThat(called).isFalse();

        uni.subscribe().withSubscriber(ts);
        ts.assertCompletedSuccessfully().assertItem(2);
        assertThat(called).isTrue();
    }

    @Test
    public void testThatValueIsRetrievedUsingBlock() {
        assertThat(Uni.createFrom().item("foo").await().indefinitely()).isEqualToIgnoringCase("foo");
    }

    @Test
    public void testWithImmediateCancellation() {
        UniAssertSubscriber<String> subscriber1 = new UniAssertSubscriber<>(true);
        UniAssertSubscriber<String> subscriber2 = new UniAssertSubscriber<>(false);
        Uni<String> foo = Uni.createFrom().item("foo");
        foo.subscribe().withSubscriber(subscriber1);
        foo.subscribe().withSubscriber(subscriber2);
        subscriber1.assertNoResult().assertNoFailure();
        subscriber2.assertCompletedSuccessfully().assertItem("foo");
    }

    @Test
    public void testEmpty() {
        UniAssertSubscriber<Void> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().voidItem().subscribe().withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(null);
    }

    @Test
    public void testEmptyTyped() {
        UniAssertSubscriber<String> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().<String> nullItem().subscribe().withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(null);
    }

    @Test
    public void testEmptyWithImmediateCancellation() {
        UniAssertSubscriber<Void> subscriber = new UniAssertSubscriber<>(true);
        Uni.createFrom().voidItem().subscribe().withSubscriber(subscriber);
        subscriber.assertNoFailure().assertNoResult();
    }

    @Test
    public void testEmptyTypedWithImmediateCancellation() {
        UniAssertSubscriber<String> subscriber = new UniAssertSubscriber<>(true);
        Uni.createFrom().<String> nullItem().subscribe().withSubscriber(subscriber);
        subscriber.assertNoFailure().assertNoResult();
    }

    @Test
    public void testWithSharedState() {
        UniAssertSubscriber<Integer> ts1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> ts2 = UniAssertSubscriber.create();
        AtomicInteger shared = new AtomicInteger();
        Uni<Integer> uni = Uni.createFrom().item(() -> shared,
                AtomicInteger::incrementAndGet);

        assertThat(shared).hasValue(0);
        uni.subscribe().withSubscriber(ts1);
        assertThat(shared).hasValue(1);
        ts1.assertCompletedSuccessfully().assertItem(1);
        uni.subscribe().withSubscriber(ts2);
        assertThat(shared).hasValue(2);
        ts2.assertCompletedSuccessfully().assertItem(2);
    }

    @Test
    public void testWithSharedStateProducingFailure() {
        UniAssertSubscriber<Integer> ts1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> ts2 = UniAssertSubscriber.create();
        Supplier<AtomicInteger> boom = () -> {
            throw new IllegalStateException("boom");
        };

        Uni<Integer> uni = Uni.createFrom().item(boom,
                AtomicInteger::incrementAndGet);

        uni.subscribe().withSubscriber(ts1);
        ts1.assertFailure(IllegalStateException.class, "boom");
        uni.subscribe().withSubscriber(ts2);
        ts2.assertFailure(IllegalStateException.class, "Invalid shared state");
    }

    @Test
    public void testWithSharedStateProducingNull() {
        UniAssertSubscriber<Integer> ts1 = UniAssertSubscriber.create();
        UniAssertSubscriber<Integer> ts2 = UniAssertSubscriber.create();
        Supplier<AtomicInteger> boom = () -> null;

        Uni<Integer> uni = Uni.createFrom().item(boom,
                AtomicInteger::incrementAndGet);

        uni.subscribe().withSubscriber(ts1);
        ts1.assertFailure(NullPointerException.class, "supplier");
        uni.subscribe().withSubscriber(ts2);
        ts2.assertFailure(IllegalStateException.class, "Invalid shared state");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatStateSupplierCannotBeNull() {
        Uni.createFrom().item(null,
                x -> "x");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatFunctionCannotBeNull() {
        Uni.createFrom().item(() -> "hello",
                null);
    }

}
