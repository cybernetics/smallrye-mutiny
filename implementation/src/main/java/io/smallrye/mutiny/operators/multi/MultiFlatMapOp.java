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
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.ParameterValidation;
import io.smallrye.mutiny.helpers.Subscriptions;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.BackPressureFailure;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.smallrye.mutiny.subscription.SafeSubscriber;
import io.smallrye.mutiny.subscription.SerializedSubscriber;

public final class MultiFlatMapOp<I, O> extends AbstractMultiOperator<I, O> {
    private final Function<? super I, ? extends Publisher<? extends O>> mapper;

    private final boolean postponeFailurePropagation;
    private final int maxConcurrency;

    private final Supplier<? extends Queue<O>> mainQueueSupplier;
    private final Supplier<? extends Queue<O>> innerQueueSupplier;

    public MultiFlatMapOp(Multi<? extends I> upstream,
            Function<? super I, ? extends Publisher<? extends O>> mapper,
            boolean postponeFailurePropagation,
            int maxConcurrency,
            Supplier<? extends Queue<O>> mainQueueSupplier,
            Supplier<? extends Queue<O>> innerQueueSupplier) {
        super(upstream);
        this.mapper = ParameterValidation.nonNull(mapper, "mapper");
        this.postponeFailurePropagation = postponeFailurePropagation;
        this.maxConcurrency = ParameterValidation.positive(maxConcurrency, "maxConcurrency");
        this.mainQueueSupplier = ParameterValidation.nonNull(mainQueueSupplier, "mainQueueSupplier");
        this.innerQueueSupplier = ParameterValidation.nonNull(innerQueueSupplier, "innerQueueSupplier");
    }

    @Override
    public void subscribe(MultiSubscriber<? super O> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("The subscriber must not be `null`");
        }
        FlatMapMainSubscriber<I, O> sub = new FlatMapMainSubscriber<>(subscriber,
                mapper,
                postponeFailurePropagation,
                maxConcurrency,
                mainQueueSupplier,
                innerQueueSupplier);
        upstream.subscribe(
                Infrastructure.onMultiSubscription(upstream, new SafeSubscriber<>(new SerializedSubscriber<>(sub))));
    }

    public static final class FlatMapMainSubscriber<I, O> extends FlatMapManager<FlatMapInner<O>>
            implements MultiSubscriber<I>, Subscription {

        final boolean delayError;
        final int maxConcurrency;
        final int limit;
        final Function<? super I, ? extends Publisher<? extends O>> mapper;
        final Supplier<? extends Queue<O>> mainQueueSupplier;
        final Supplier<? extends Queue<O>> innerQueueSupplier;
        final MultiSubscriber<? super O> downstream;

        volatile Queue<O> queue;

        AtomicReference<Throwable> failures = new AtomicReference<>();

        volatile boolean done;
        volatile boolean cancelled;

        AtomicReference<Subscription> upstream = new AtomicReference<>();

        AtomicLong requested = new AtomicLong();

        AtomicInteger wip = new AtomicInteger();

        @SuppressWarnings("rawtypes")
        static final FlatMapInner[] EMPTY_INNER_ARRAY = new FlatMapInner[0];

        @SuppressWarnings("rawtypes")
        static final FlatMapInner[] TERMINATED_INNER_ARRAY = new FlatMapInner[0];

        int lastIndex;

        FlatMapMainSubscriber(MultiSubscriber<? super O> downstream,
                Function<? super I, ? extends Publisher<? extends O>> mapper,
                boolean delayError,
                int maxConcurrency,
                Supplier<? extends Queue<O>> mainQueueSupplier,
                Supplier<? extends Queue<O>> innerQueueSupplier) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.delayError = delayError;
            this.maxConcurrency = maxConcurrency;
            this.mainQueueSupplier = mainQueueSupplier;
            this.innerQueueSupplier = innerQueueSupplier;
            this.limit = Subscriptions.unboundedOrLimit(maxConcurrency);
        }

        @SuppressWarnings("unchecked")
        @Override
        FlatMapInner<O>[] empty() {
            return EMPTY_INNER_ARRAY;
        }

        @SuppressWarnings("unchecked")
        @Override
        FlatMapInner<O>[] terminated() {
            return TERMINATED_INNER_ARRAY;
        }

        @SuppressWarnings("unchecked")
        @Override
        FlatMapInner<O>[] newArray(int size) {
            return new FlatMapInner[size];
        }

        @Override
        void setIndex(FlatMapInner<O> entry, int index) {
            entry.index = index;
        }

        @Override
        void unsubscribeEntry(FlatMapInner<O> entry, boolean fromOnError) {
            entry.cancel(fromOnError);
        }

        @Override
        public void request(long n) {
            if (n > 0) {
                Subscriptions.add(requested, n);
                drain();
            } else {
                downstream.onFailure(new IllegalArgumentException("Invalid requests, must be greater than 0"));
            }
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;

                if (wip.getAndIncrement() == 0) {
                    clearQueue();
                    upstream.getAndSet(Subscriptions.CANCELLED).cancel();
                    unsubscribe();
                }
            }
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (upstream.compareAndSet(null, s)) {
                downstream.onSubscribe(this);
                s.request(Subscriptions.unboundedOrMaxConcurrency(maxConcurrency));
            }
        }

        @Override
        public void onItem(I item) {
            if (done) {
                return;
            }

            Publisher<? extends O> p;

            try {
                p = mapper.apply(item);
                if (p == null) {
                    throw new NullPointerException(ParameterValidation.MAPPER_RETURNED_NULL);
                }
            } catch (Throwable e) {
                cancelled = true;
                done = true;
                Subscriptions.addFailure(failures, e);
                cancelUpstream(false);
                handleTerminationIfDone();
                return;
            }

            FlatMapInner<O> inner = new FlatMapInner<>(this, maxConcurrency);
            if (add(inner)) {
                p.subscribe(inner);
            }
        }

        @Override
        public void onFailure(Throwable failure) {
            if (done) {
                return;
            }
            Subscriptions.addFailure(failures, failure);
            done = true;
            if (!delayError) {
                for (FlatMapInner<O> inner : inners.getAndSet(terminated())) {
                    if (inner != null) {
                        inner.cancel(false);
                    }
                }
            }
            drain();
        }

        @Override
        public void onCompletion() {
            if (done) {
                return;
            }

            done = true;
            drain();
        }

        void tryEmit(FlatMapInner<O> inner, O item) {
            if (wip.compareAndSet(0, 1)) {
                long req = requested.get();
                Queue<O> q = inner.queue;
                if (req != 0 && (q == null || q.isEmpty())) {
                    downstream.onNext(item);

                    if (req != Long.MAX_VALUE) {
                        requested.decrementAndGet();
                    }

                    inner.request(1);
                } else {
                    if (q == null) {
                        q = getOrCreateInnerQueue(inner);
                    }

                    if (!q.offer(item)) {
                        failOverflow();
                        inner.done = true;
                        drainLoop();
                        return;
                    }
                }
                if (wip.decrementAndGet() == 0) {
                    return;
                }

                drainLoop();
            } else {
                Queue<O> q = getOrCreateInnerQueue(inner);

                if (!q.offer(item)) {
                    failOverflow();
                    inner.done = true;
                }
                drain();
            }
        }

        void drain() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            drainLoop();
        }

        void drainLoop() {
            int missed = 1;

            final MultiSubscriber<? super O> a = downstream;

            for (;;) {

                boolean d;

                FlatMapInner<O>[] as = get();

                int n = as.length;

                Queue<O> sq = queue;

                boolean noSources = isEmpty();

                if (ifDoneOrCancelled()) {
                    return;
                }

                boolean again = false;

                long r = requested.get();
                long e = 0L;
                long replenishMain = 0L;

                if (r != 0L && sq != null) {

                    while (e != r) {
                        d = done;

                        O v = sq.poll();

                        boolean empty = v == null;

                        if (ifDoneOrCancelled()) {
                            return;
                        }

                        if (empty) {
                            break;
                        }

                        a.onItem(v);

                        e++;
                    }

                    if (e != 0L) {
                        replenishMain += e;
                        if (r != Long.MAX_VALUE) {
                            r = requested.addAndGet(-e);
                        }
                        e = 0L;
                        again = true;
                    }
                }
                if (r != 0L && !noSources) {

                    int j = lastIndex;
                    for (int i = 0; i < n; i++) {
                        if (cancelled) {
                            cancelUpstream(false);
                            return;
                        }

                        FlatMapInner<O> inner = as[j];
                        if (inner != null) {
                            d = inner.done;
                            Queue<O> q = inner.queue;
                            if (d && q == null) {
                                remove(inner.index);
                                again = true;
                                replenishMain++;
                            } else if (q != null) {
                                while (e != r) {
                                    d = inner.done;

                                    O v;

                                    try {
                                        v = q.poll();
                                    } catch (Throwable ex) {
                                        Subscriptions.addFailure(failures, ex);
                                        v = null;
                                        d = true;
                                    }

                                    boolean empty = v == null;

                                    if (ifDoneOrCancelled()) {
                                        return;
                                    }

                                    if (d && empty) {
                                        remove(inner.index);
                                        again = true;
                                        replenishMain++;
                                        break;
                                    }

                                    if (empty) {
                                        break;
                                    }

                                    a.onItem(v);

                                    e++;
                                }

                                if (e == r) {
                                    d = inner.done;
                                    boolean empty = q.isEmpty();
                                    if (d && empty) {
                                        remove(inner.index);
                                        again = true;
                                        replenishMain++;
                                    }
                                }

                                if (e != 0L) {
                                    if (!inner.done) {
                                        inner.request(e);
                                    }
                                    if (r != Long.MAX_VALUE) {
                                        r = requested.addAndGet(-e);
                                        if (r == 0L) {
                                            break; // 0 .. numberOfItems - 1
                                        }
                                    }
                                    e = 0L;
                                }
                            }
                        }

                        if (r == 0L) {
                            break;
                        }

                        if (++j == n) {
                            j = 0;
                        }
                    }

                    lastIndex = j;
                }

                if (r == 0L && !noSources) {
                    as = get();
                    n = as.length;

                    for (int i = 0; i < n; i++) {
                        if (cancelled) {
                            cancelUpstream(false);
                            return;
                        }

                        FlatMapInner<O> inner = as[i];
                        if (inner == null) {
                            continue;
                        }

                        d = inner.done;
                        Queue<O> q = inner.queue;
                        boolean empty = (q == null || q.isEmpty());

                        // if we have a non-empty source then quit the cleanup
                        if (!empty) {
                            break;
                        }

                        if (d && empty) {
                            remove(inner.index);
                            again = true;
                            replenishMain++;
                        }
                    }
                }

                if (replenishMain != 0L && !done && !cancelled) {
                    upstream.get().request(replenishMain);
                }

                if (again) {
                    continue;
                }

                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        private void cancelUpstream(boolean fromOnError) {
            clearQueue();
            Subscription subscription = upstream.getAndSet(Subscriptions.CANCELLED);
            if (subscription != null) {
                subscription.cancel();
            }
            unsubscribe(fromOnError);
        }

        private void clearQueue() {
            if (queue != null) {
                queue.clear();
                queue = null;
            }
        }

        boolean ifDoneOrCancelled() {
            if (cancelled) {
                cancelUpstream(false);
                return true;
            }

            return handleTerminationIfDone();

        }

        private boolean handleTerminationIfDone() {
            boolean wasDone = done;
            boolean isEmpty = isEmpty() && (queue == null || queue.isEmpty());
            if (delayError) {
                if (wasDone && isEmpty) {
                    Throwable e = failures.get();
                    if (e != null && e != Subscriptions.TERMINATED) {
                        Throwable throwable = failures.getAndSet(Subscriptions.TERMINATED);
                        downstream.onFailure(throwable);
                    } else {
                        downstream.onCompletion();
                    }
                    return true;
                }
            } else {
                if (wasDone) {
                    Throwable e = failures.get();
                    if (e != null && e != Subscriptions.TERMINATED) {
                        Throwable throwable = failures.getAndSet(Subscriptions.TERMINATED);
                        clearQueue();
                        unsubscribe(true);
                        downstream.onFailure(throwable);
                        return true;
                    } else if (isEmpty) {
                        downstream.onCompletion();
                        return true;
                    }
                }
            }
            return false;
        }

        void innerError(FlatMapInner<O> inner, Throwable fail) {
            if (fail != null) {
                if (Subscriptions.addFailure(failures, fail)) {
                    inner.done = true;
                    if (!delayError) {
                        cancelUpstream(true);
                        downstream.onFailure(fail);
                        return;
                    }
                    drain();
                }
            } else {
                drain();
            }
        }

        void failOverflow() {
            Throwable e = new BackPressureFailure("Buffer full, cannot emit item");
            Subscriptions.addFailure(failures, e);
        }

        void innerComplete() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            drainLoop();
        }

        Queue<O> getOrCreateInnerQueue(FlatMapInner<O> inner) {
            Queue<O> q = inner.queue;
            if (q == null) {
                q = innerQueueSupplier.get();
                inner.queue = q;
            }
            return q;
        }

    }

    static final class FlatMapInner<O> implements Subscription, MultiSubscriber<O> {

        final FlatMapMainSubscriber<?, O> parent;

        final int prefetch;

        final int limit;

        final AtomicReference<Subscription> subscription = new AtomicReference<>();

        long produced;

        volatile Queue<O> queue;

        volatile boolean done;

        int index;

        FlatMapInner(FlatMapMainSubscriber<?, O> parent, int prefetch) {
            this.parent = parent;
            this.prefetch = prefetch;
            this.limit = Subscriptions.unboundedOrLimit(prefetch);
        }

        @Override
        public void onSubscribe(Subscription s) {
            Objects.requireNonNull(s);
            if (subscription.compareAndSet(null, s)) {
                s.request(Subscriptions.unboundedOrMaxConcurrency(prefetch));
            }
        }

        @Override
        public void onItem(O item) {
            parent.tryEmit(this, item);
        }

        @Override
        public void onFailure(Throwable failure) {
            Objects.requireNonNull(failure);
            done = true;
            parent.innerError(this, failure);
        }

        @Override
        public void onCompletion() {
            done = true;
            parent.innerComplete();
        }

        @Override
        public void request(long n) {
            long p = produced + n;
            if (p >= limit) {
                produced = 0L;
                subscription.get().request(p);
            } else {
                produced = p;
            }
        }

        @Override
        public void cancel() {
            cancel(true);
        }

        public void cancel(boolean doNotCancel) {
            if (!doNotCancel) {
                Subscription last = subscription.getAndSet(Subscriptions.CANCELLED);
                if (last != null) {
                    last.cancel();
                }
            }
            if (queue != null) {
                queue.clear();
                queue = null;
            }
        }
    }
}
