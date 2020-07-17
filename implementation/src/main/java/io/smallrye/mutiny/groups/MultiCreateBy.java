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

import io.smallrye.mutiny.Multi;

public class MultiCreateBy {
    public static final MultiCreateBy INSTANCE = new MultiCreateBy();

    private MultiCreateBy() {
        // Avoid direct instantiation
    }

    /**
     * Creates a new instance of {@link Multi} by concatenating several
     * {@link Multi} or {@link org.reactivestreams.Publisher} instances.
     * <p>
     * The concatenation reads the streams in order and emits the items in order.
     *
     * @return the object to configure the concatenation
     */
    public MultiConcat concatenating() {
        return new MultiConcat(false);
    }

    /**
     * Creates a new instance of {@link Multi} by merging several
     * {@link Multi} or {@link org.reactivestreams.Publisher} instances.
     * <p>
     * The concatenation reads the streams in parallel and emits the items as they come.
     *
     * @return the object to configure the merge
     */
    public MultiMerge merging() {
        return new MultiMerge(false, 128, 128);
    }

    /**
     * Creates a new instance of {@link Multi} by associating / combining the items from different
     * streams ({@link Multi} or {@link org.reactivestreams.Publisher}).
     * <p>
     * The resulting {@link Multi} can:
     * <ul>
     * <li>collects an item of every observed streams and combine them. If one of the observed stream sends the
     * completion event, the event is propagated in the produced stream, and no other combination are emitted.</li>
     * <li>as soon as on of the observed stream emits an item, it combine it with the latest items for the other stream.
     * the completion event is sent when all the observed streams have completed (with a completion event).</li>
     * </ul>
     * <p>
     * The combination also allows to collect the failures and propagate a failure when all observed streams have completed
     * (or failed) instead of propagating the failure immediately.
     *
     * @return the object to configure the combination
     */
    public MultiItemCombination combining() {
        return new MultiItemCombination();
    }

    /**
     * Creates a new {@link Multi} by repeating a given function producing {@link io.smallrye.mutiny.Uni unis} or
     * {@link java.util.concurrent.CompletionStage Completion Stages}.
     *
     * @return the object to configure the repetition
     */
    public MultiRepetition repeating() {
        return new MultiRepetition();
    }

}
