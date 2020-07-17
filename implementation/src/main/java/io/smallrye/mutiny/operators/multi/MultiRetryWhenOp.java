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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.mutiny.helpers.Subscriptions;
import io.smallrye.mutiny.operators.AbstractMulti;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.smallrye.mutiny.subscription.SerializedSubscriber;
import io.smallrye.mutiny.subscription.SwitchableSubscriptionSubscriber;

/**
 * Retries a source when a companion stream signals an item in response to the main's failure event.
 * <p>
 * If the companion stream signals when the main source is active, the repeat
 * attempt is suppressed and any terminal signal will terminate the main source with the same signal immediately.
 *
 * @param <T> the type of item
 */
public final class MultiRetryWhenOp<T> extends AbstractMultiOperator<T, T> {

    private final Function<? super Multi<Throwable>, ? extends Publisher<?>> triggerStreamFactory;

    public MultiRetryWhenOp(Multi<? extends T> upstream,
            Function<? super Multi<Throwable>, ? extends Publisher<?>> triggerStreamFactory) {
        super(upstream);
        this.triggerStreamFactory = ParameterValidation.nonNull(triggerStreamFactory, "triggerStreamFactory");
    }

    private static <T> void subscribe(MultiSubscriber<? super T> downstream,
            Function<? super Multi<Throwable>, ? extends Publisher<?>> triggerStreamFactory,
            Multi<? extends T> upstream) {
        TriggerSubscriber other = new TriggerSubscriber();
        Subscriber<Throwable> signaller = new SerializedSubscriber<>(other.processor);
        signaller.onSubscribe(Subscriptions.empty());
        MultiSubscriber<T> serialized = new SerializedSubscriber<>(downstream);

        RetryWhenOperator<T> operator = new RetryWhenOperator<>(upstream, serialized, signaller);
        other.operator = operator;

        serialized.onSubscribe(operator);
        Publisher<?> publisher;

        try {
            publisher = triggerStreamFactory.apply(other);
            if (publisher == null) {
                throw new NullPointerException("The stream factory returned `null`");
            }
        } catch (Throwable e) {
            downstream.onFailure(e);
            return;
        }

        publisher.subscribe(other);

        if (!operator.isCancelled()) {
            upstream.subscribe(operator);
        }
    }

    @Override
    public void subscribe(MultiSubscriber<? super T> downstream) {
        subscribe(downstream, triggerStreamFactory, upstream);
    }

    static final class RetryWhenOperator<T> extends SwitchableSubscriptionSubscriber<T> {

        private final Publisher<? extends T> upstream;
        private final AtomicInteger wip = new AtomicInteger();
        private final Subscriber<Throwable> signaller;
        private final Subscriptions.DeferredSubscription arbiter = new Subscriptions.DeferredSubscription();

        long produced;

        RetryWhenOperator(Publisher<? extends T> upstream, MultiSubscriber<? super T> downstream,
                Subscriber<Throwable> signaller) {
            super(downstream);
            this.upstream = upstream;
            this.signaller = signaller;
        }

        @Override
        public void cancel() {
            if (!isCancelled()) {
                arbiter.cancel();
                super.cancel();
            }

        }

        public void setWhen(Subscription w) {
            arbiter.set(w);
        }

        @Override
        public void onItem(T t) {
            downstream.onItem(t);
            produced++;
        }

        @Override
        public void onFailure(Throwable t) {
            long p = produced;
            if (p != 0L) {
                produced = 0;
                emitted(p);
            }
            arbiter.request(1);
            signaller.onNext(t);
        }

        @Override
        public void onCompletion() {
            arbiter.cancel();
            downstream.onComplete();
        }

        void resubscribe() {
            if (wip.getAndIncrement() == 0) {
                do {
                    if (isCancelled()) {
                        return;
                    }

                    upstream.subscribe(this);

                } while (wip.decrementAndGet() != 0);
            }
        }

        void whenFailure(Throwable failure) {
            super.cancel();
            downstream.onFailure(failure);
        }

        void whenComplete() {
            super.cancel();
            downstream.onComplete();
        }
    }

    @SuppressWarnings({ "SubscriberImplementation" })
    static final class TriggerSubscriber extends AbstractMulti<Throwable>
            implements Multi<Throwable>, Subscriber<Object> {
        RetryWhenOperator<?> operator;
        private final Processor<Throwable, Throwable> processor = UnicastProcessor.<Throwable> create().serialized();

        @Override
        public void onSubscribe(Subscription s) {
            operator.setWhen(s);
        }

        @Override
        public void onNext(Object t) {
            operator.resubscribe();
        }

        @Override
        public void onError(Throwable t) {
            operator.whenFailure(t);
        }

        @Override
        public void onComplete() {
            operator.whenComplete();
        }

        @Override
        public void subscribe(Subscriber<? super Throwable> actual) {
            processor.subscribe(actual);
        }
    }

}
