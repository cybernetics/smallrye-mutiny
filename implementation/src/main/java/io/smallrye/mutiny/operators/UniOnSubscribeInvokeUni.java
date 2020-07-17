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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.EmptyUniSubscription;
import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.mutiny.subscription.UniSubscription;

public class UniOnSubscribeInvokeUni<T> extends UniOperator<T, T> {

    private final Function<? super UniSubscription, Uni<?>> callback;

    public UniOnSubscribeInvokeUni(Uni<? extends T> upstream,
            Function<? super UniSubscription, Uni<?>> callback) {
        super(ParameterValidation.nonNull(upstream, "upstream"));
        this.callback = callback;
    }

    @Override
    protected void subscribing(UniSerializedSubscriber<? super T> subscriber) {
        AbstractUni.subscribe(upstream(), new UniDelegatingSubscriber<T, T>(subscriber) {

            // As subscription might be delayed, we need to store the event provided by the upstream
            // until the uni provides a item or failure event. It would be illegal to forward these events before a
            // subscription.

            volatile T item;
            volatile Throwable failure;

            final AtomicBoolean done = new AtomicBoolean();

            @Override
            public void onSubscribe(UniSubscription subscription) {
                // Invoke producer
                Uni<?> uni;
                try {
                    uni = Objects.requireNonNull(callback.apply(subscription), "The produced Uni must not be `null`");
                } catch (Throwable e) {
                    // If the functions fails or returns null, propagates a failure.
                    super.onSubscribe(EmptyUniSubscription.CANCELLED);
                    super.onFailure(e);
                    return;
                }

                uni.subscribe().with(
                        ignored -> {
                            // Once the uni produces its item, propagates the subscription downstream
                            super.onSubscribe(subscription);
                            if (done.compareAndSet(false, true)) {
                                forwardPendingEvent();
                            }
                        },
                        failed -> {
                            // On failure, propagates the failure
                            super.onSubscribe(EmptyUniSubscription.CANCELLED);
                            super.onFailure(failed);
                        });
            }

            private void forwardPendingEvent() {
                if (item != null) {
                    super.onItem(item);
                } else if (failure != null) {
                    super.onFailure(failure);
                }
            }

            @Override
            public void onItem(T item) {
                if (done.get()) {
                    super.onItem(item);
                } else {
                    this.item = item;
                }
            }

            @Override
            public void onFailure(Throwable failure) {
                if (done.get()) {
                    super.onFailure(failure);
                } else {
                    this.failure = failure;
                }
            }
        });
    }
}
