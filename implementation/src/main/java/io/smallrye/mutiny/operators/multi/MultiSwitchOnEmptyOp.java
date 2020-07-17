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
package io.smallrye.mutiny.operators.multi;

import java.util.Objects;

import org.reactivestreams.Publisher;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.smallrye.mutiny.subscription.SwitchableSubscriptionSubscriber;

/**
 * Switches to another Multi if the upstream is empty (completes without having emitted any items).
 */
public final class MultiSwitchOnEmptyOp<T> extends AbstractMultiOperator<T, T> {

    private final Publisher<? extends T> alternative;

    public MultiSwitchOnEmptyOp(Multi<? extends T> upstream, Publisher<? extends T> alternative) {
        super(upstream);
        this.alternative = Objects.requireNonNull(alternative, "alternative");
    }

    @Override
    public void subscribe(MultiSubscriber<? super T> actual) {
        SwitchIfEmptySubscriber<T> parent = new SwitchIfEmptySubscriber<>(actual, alternative);
        actual.onSubscribe(parent);
        upstream.subscribe().withSubscriber(parent);
    }

    static final class SwitchIfEmptySubscriber<T> extends SwitchableSubscriptionSubscriber<T> {

        private final Publisher<? extends T> alternative;
        boolean notEmpty;

        SwitchIfEmptySubscriber(MultiSubscriber<? super T> downstream,
                Publisher<? extends T> alternative) {
            super(downstream);
            this.alternative = alternative;
        }

        @Override
        public void onItem(T t) {
            if (!notEmpty) {
                notEmpty = true;
            }
            downstream.onItem(t);
        }

        @Override
        public void onCompletion() {
            if (!notEmpty) {
                notEmpty = true;
                alternative.subscribe(Infrastructure.onMultiSubscription(alternative, this));
            } else {
                downstream.onCompletion();
            }
        }
    }
}
