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
package io.smallrye.mutiny.groups;

import static io.smallrye.mutiny.helpers.ParameterValidation.nonNull;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.UniBlockingAwait;

/**
 * Waits and returns the item emitted by the {@link Uni}. If the {@link Uni} receives a failure, the failure is thrown.
 * <p>
 * This class lets you configure how to retrieves the item of a {@link Uni} by blocking the caller thread.
 *
 * @param <T> the type of item
 * @see Uni#await()
 */
public class UniAwait<T> {

    private final Uni<T> upstream;

    public UniAwait(Uni<T> upstream) {
        this.upstream = nonNull(upstream, "upstream");
    }

    /**
     * Subscribes to the {@link Uni} and waits (blocking the caller thread) <strong>indefinitely</strong> until a
     * {@code item} event is fired or a {@code failure} event is fired by the upstream uni.
     * <p>
     * If the {@link Uni} fires an item, it returns that item, potentially {@code null} if the operation
     * returns {@code null}.
     * If the {@link Uni} fires a failure, the original exception is thrown (wrapped in
     * a {@link java.util.concurrent.CompletionException} it's a checked exception).
     * <p>
     * Note that each call to this method triggers a new subscription.
     *
     * @return the item from the {@link Uni}, potentially {@code null}
     */
    public T indefinitely() {
        return atMost(null);
    }

    /**
     * Subscribes to the {@link Uni} and waits (blocking the caller thread) <strong>at most</strong> the given duration
     * until an item or failure is fired by the upstream uni.
     * <p>
     * If the {@link Uni} fires an item, it returns that item, potentially {@code null} if the operation
     * returns {@code null}.
     * If the {@link Uni} fires a failure, the original exception is thrown (wrapped in
     * a {@link java.util.concurrent.CompletionException} it's a checked exception).
     * If the timeout is reached before completion, a {@link TimeoutException} is thrown.
     * <p>
     * Note that each call to this method triggers a new subscription.
     *
     * @param duration the duration, must not be {@code null}, must not be negative or zero.
     * @return the item from the {@link Uni}, potentially {@code null}
     */
    public T atMost(Duration duration) {
        return UniBlockingAwait.await(upstream, duration);
    }

    /**
     * Indicates that you are awaiting for the item event of the attached {@link Uni} wrapped into an {@link Optional}.
     * So if the {@link Uni} fires {@code null} as item, you receive an empty {@link Optional}.
     *
     * @return the {@link UniAwaitOptional} configured to produce an {@link Optional}.
     */
    public UniAwaitOptional<T> asOptional() {
        return new UniAwaitOptional<>(upstream);
    }

}
