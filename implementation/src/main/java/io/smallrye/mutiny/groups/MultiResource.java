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

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.mutiny.operators.multi.builders.ResourceMulti;

/**
 * Allows configuring a <em>finalizer</em> to close the resource attached to the stream.
 *
 * @param <R> the type of resource
 * @param <I> the type of item emitted by the resulting {@code Multi}
 * @see MultiCreate#resource(Supplier, Function)
 */
public class MultiResource<R, I> {
    private final Function<? super R, ? extends Publisher<I>> streamSupplier;
    private final Supplier<? extends R> resourceSupplier;

    public MultiResource(Supplier<? extends R> resourceSupplier,
            Function<? super R, ? extends Publisher<I>> streamSupplier) {
        this.resourceSupplier = resourceSupplier;
        this.streamSupplier = streamSupplier;
    }

    /**
     * Configures a <em>synchronous</em> finalizer. The given function is called when the stream completes, fails or
     * when the subscriber cancels.
     * If the finalizer throws an exception, this exception is propagated to the subscriber, unless it has already
     * cancelled.
     *
     * @param finalizer the finalizer, must not be {@code null}
     * @return the multi
     */
    public Multi<I> withFinalizer(Consumer<? super R> finalizer) {
        ParameterValidation.nonNull(finalizer, "finalizer");
        Function<? super R, Uni<Void>> actual = r -> {
            finalizer.accept(r);
            return Uni.createFrom().voidItem();
        };
        return withFinalizer(actual, (r, ignored) -> actual.apply(r), actual);
    }

    /**
     * Configures an <em>asynchronous</em> finalizer. The given function is called when the stream completes, fails or
     * when the subscriber cancels. The returned {@code Uni} is flattened with the stream meaning that the subscriber
     * gets the events fired by the {@code Uni}. If the {@link Uni} completes successfully, the subscriber gets
     * the {@code completion} event. If the {@link Uni} fails, the subscriber gets the failure even if the resource
     * stream completed successfully. If the {@link Uni} fails after a resource stream failure, the subscriber receives
     * a {@link io.smallrye.mutiny.CompositeException}. If the subscribers cancels, the {@link Uni} outcome is ignored.
     * <p>
     * If the finalizer throws an exception, this exception is propagated to the subscriber, unless it has already
     * cancelled.
     * If the finalizer returns {@code null}, a {@link NullPointerException} is propagated to the subscriber, unless it
     * has already cancelled.
     *
     * @param finalizer the finalizer, must not be {@code null}
     * @return the multi
     */
    public Multi<I> withFinalizer(Function<? super R, Uni<Void>> finalizer) {
        Function<? super R, Uni<Void>> actual = ParameterValidation.nonNull(finalizer, "finalizer");
        return withFinalizer(actual, (r, ignored) -> actual.apply(r), actual);
    }

    /**
     * Configures <em>asynchronous</em> finalizers distinct for each event. The given functions are called when the
     * stream completes, fails or when the subscriber cancels.
     * <p>
     * The returned {@code Uni} is flattened with the stream meaning that the subscriber
     * gets the events fired by the {@code Uni}. If the {@link Uni} completes successfully, the subscriber gets
     * the {@code completion} event. If the {@link Uni} fails, the subscriber gets the failure even if the resource
     * stream completed successfully. If the {@link Uni} fails after a resource stream failure, the subscriber receives
     * a {@link io.smallrye.mutiny.CompositeException}. If the subscribers cancels, the {@link Uni} outcome is ignored.
     * <p>
     * If a finalizer throws an exception, this exception is propagated to the subscriber, unless it has already
     * cancelled.
     * If a finalizer returns {@code null}, a {@link NullPointerException} is propagated to the subscriber, unless it
     * has already cancelled.
     *
     * @param onCompletion the completion finalizer called when the resource stream completes successfully. Must not be
     *        {@code null}
     * @param onFailure the failure finalizer called when the resource stream propagated a failure. The finalizer is
     *        called with the resource and the failure. Must not be {@code null}
     * @param onCancellation the cancellation finalizer called when the subscribers cancels the subscription. Must not
     *        be {@code null}.
     * @return the multi
     */
    public Multi<I> withFinalizer(
            Function<? super R, Uni<Void>> onCompletion,
            BiFunction<? super R, ? super Throwable, Uni<Void>> onFailure,
            Function<? super R, Uni<Void>> onCancellation) {
        return new ResourceMulti<>(resourceSupplier, streamSupplier,
                ParameterValidation.nonNull(onCompletion, "onCompletion"),
                ParameterValidation.nonNull(onFailure, "onFailure"),
                ParameterValidation.nonNull(onCancellation, "onCancellation"));
    }
}
