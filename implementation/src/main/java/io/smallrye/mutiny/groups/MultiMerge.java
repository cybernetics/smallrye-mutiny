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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.reactivestreams.Publisher;

import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.MultiCombine;

/**
 * Creates new {@link Multi} by merging several {@link Multi} or {@link Publisher}.
 * This class allows configuring how the merge is executed. Unlike a concatenation, a merge emits the items as they
 * come, so the items may be interleaved.
 */
public class MultiMerge {

    private final boolean collectFailures;
    private final int requests;
    private final int concurrency;

    MultiMerge(boolean collectFailures, int requests, int concurrency) {
        this.collectFailures = collectFailures;
        this.requests = requests;
        this.concurrency = concurrency;
    }

    /**
     * Creates a new {@link Multi} merging the items emitted by the given {@link Multi multis} /
     * {@link Publisher publishers}.
     *
     * @param publishers the publishers, must not be empty, must not contain {@code null}
     * @param <T> the type of item
     * @return the new {@link Multi} emitting the items from the given set of {@link Multi}
     */
    @SafeVarargs
    public final <T> Multi<T> streams(Publisher<T>... publishers) {
        return MultiCombine.merge(Arrays.asList(publishers), collectFailures, requests, concurrency);
    }

    /**
     * Creates a new {@link Multi} merging the items emitted by the given {@link Publisher publishers} /
     * {@link Publisher publishers}.
     *
     * @param iterable the published, must not be empty, must not contain {@code null}, must not be {@code null}
     * @param <T> the type of item
     * @return the new {@link Multi} emitting the items from the given set of {@link Publisher}
     */
    public <T> Multi<T> streams(Iterable<Publisher<T>> iterable) {
        List<Publisher<T>> list = new ArrayList<>();
        iterable.forEach(list::add);
        return MultiCombine.merge(list, collectFailures, requests, concurrency);
    }

    /**
     * Indicates that the merge process should not propagate the first receive failure, but collect them until
     * all the items from all (non-failing) participants have been emitted. Then, the failures are propagated downstream
     * (as a {@link CompositeException} if several failures have been received).
     *
     * @return a new {@link MultiMerge} collecting failures
     */
    public MultiMerge collectFailures() {
        return new MultiMerge(true, this.requests, this.concurrency);
    }

    /**
     * Indicates that the merge process should consume the different streams using the given {@code request}.
     *
     * @param requests the request
     * @return a new {@link MultiMerge} configured with the given requests
     */
    public MultiMerge withRequests(int requests) {
        return new MultiMerge(this.collectFailures, requests, this.concurrency);
    }

    /**
     * Indicates that the merge process can consume up to {@code concurrency} streams in parallel. Items emitted by these
     * streams may be interleaved in the resulting stream.
     *
     * @param concurrency the concurrency
     * @return a new {@link MultiMerge} configured with the given concurrency
     */
    public MultiMerge withConcurrency(int concurrency) {
        return new MultiMerge(this.collectFailures, this.requests, concurrency);
    }

}
