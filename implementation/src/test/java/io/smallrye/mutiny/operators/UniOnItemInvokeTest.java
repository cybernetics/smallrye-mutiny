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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;

public class UniOnItemInvokeTest {

    private final Uni<Integer> one = Uni.createFrom().item(1);
    private final Uni<Integer> failed = Uni.createFrom().failure(new IOException("boom"));
    private final Uni<Integer> two = Uni.createFrom().item(2);

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatConsumerMustNotBeNull() {
        Uni.createFrom().item(1).onItem().invoke(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatMapperMustNotBeNull() {
        Uni.createFrom().item(1).onItem().invokeUni(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatConsumerMustNotBeNullWithShortcut() {
        Uni.createFrom().item(1).invoke(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatMapperMustNotBeNullWithShortcut() {
        Uni.createFrom().item(1).invokeUni(null);
    }

    @Test
    public void testInvokeOnItem() {
        AtomicInteger res = new AtomicInteger();

        int r = one.onItem().invoke(res::set)
                .await().indefinitely();

        assertThat(r).isEqualTo(1);
        assertThat(res).hasValue(1);
    }

    @Test
    public void testInvokeOnItemWithShortcut() {
        AtomicInteger res = new AtomicInteger();

        int r = one.invoke(res::set)
                .await().indefinitely();

        assertThat(r).isEqualTo(1);
        assertThat(res).hasValue(1);
    }

    @Test
    public void testInvokeOnFailure() {
        AtomicInteger res = new AtomicInteger(-1);

        assertThatThrownBy(() -> failed.onItem().invoke(res::set).await().indefinitely())
                .isInstanceOf(CompletionException.class).hasCauseInstanceOf(IOException.class)
                .hasMessageContaining("boom");

        assertThat(res).hasValue(-1);
    }

    @Test
    public void testFailureInCallback() {
        AtomicInteger res = new AtomicInteger();

        assertThatThrownBy(() -> one.onItem().invoke(i -> {
            res.set(i);
            throw new RuntimeException("boom");
        }).await().indefinitely()).isInstanceOf(RuntimeException.class).hasMessageContaining("boom");

        assertThat(res).hasValue(1);
    }

    @Test
    public void testInvokeUniOnItem() {
        AtomicInteger res = new AtomicInteger();
        AtomicInteger twoGotCalled = new AtomicInteger();

        int r = one.onItem().invokeUni(i -> {
            res.set(i);
            return two.onItem().invoke(twoGotCalled::set);
        })
                .await().indefinitely();

        assertThat(r).isEqualTo(1);
        assertThat(twoGotCalled).hasValue(2);
        assertThat(res).hasValue(1);
    }

    @Test
    public void testInvokeUniOnItemWithShortcut() {
        AtomicInteger res = new AtomicInteger();
        AtomicInteger twoGotCalled = new AtomicInteger();

        int r = one.invokeUni(i -> {
            res.set(i);
            return two.invoke(twoGotCalled::set);
        })
                .await().indefinitely();

        assertThat(r).isEqualTo(1);
        assertThat(twoGotCalled).hasValue(2);
        assertThat(res).hasValue(1);
    }

    @Test
    public void testInvokeUniOnFailure() {
        AtomicInteger res = new AtomicInteger(-1);
        AtomicInteger twoGotCalled = new AtomicInteger(-1);

        assertThatThrownBy(() -> failed.onItem().invokeUni(i -> {
            res.set(i);
            return two.onItem().invoke(twoGotCalled::set);
        }).await().indefinitely()).isInstanceOf(CompletionException.class).hasCauseInstanceOf(IOException.class)
                .hasMessageContaining("boom");

        assertThat(twoGotCalled).hasValue(-1);
        assertThat(res).hasValue(-1);
    }

    @Test
    public void testFailureInAsyncCallback() {
        AtomicInteger res = new AtomicInteger();

        assertThatThrownBy(() -> one.onItem().invokeUni(i -> {
            res.set(i);
            throw new RuntimeException("boom");
        }).await().indefinitely()).isInstanceOf(RuntimeException.class).hasMessageContaining("boom");

        assertThat(res).hasValue(1);
    }

    @Test
    public void testNullReturnedByAsyncCallback() {
        AtomicInteger res = new AtomicInteger();

        assertThatThrownBy(() -> one.onItem().invokeUni(i -> {
            res.set(i);
            return null;
        }).await().indefinitely()).isInstanceOf(NullPointerException.class).hasMessageContaining("null");

        assertThat(res).hasValue(1);
    }

    @Test
    public void testInvokeUniWithSubFailure() {
        AtomicInteger res = new AtomicInteger(-1);
        AtomicInteger twoGotCalled = new AtomicInteger(-1);

        assertThatThrownBy(() -> one.onItem().invokeUni(i -> {
            res.set(i);
            return two.onItem().invoke(twoGotCalled::set)
                    .onItem().failWith(k -> new IllegalStateException("boom-" + k));
        }).await().indefinitely()).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom-2");

        assertThat(twoGotCalled).hasValue(2);
        assertThat(res).hasValue(1);
    }

    @Test
    public void testCancellationBeforeActionCompletes() {
        AtomicBoolean terminated = new AtomicBoolean();
        Uni<Object> uni = Uni.createFrom().emitter(e -> e.onTermination(() -> terminated.set(true)));

        AtomicInteger result = new AtomicInteger();
        Cancellable cancellable = one.onItem().invokeUni(i -> uni).subscribe().with(result::set);

        cancellable.cancel();
        assertThat(result).hasValue(0);
        //noinspection ConstantConditions
        assertThat(terminated).isTrue();
    }

}