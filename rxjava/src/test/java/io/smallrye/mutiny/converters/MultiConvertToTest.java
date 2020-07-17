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
package io.smallrye.mutiny.converters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.converters.multi.MultiRxConverters;
import io.smallrye.mutiny.test.MultiAssertSubscriber;

public class MultiConvertToTest {

    @Test
    public void testCreatingACompletable() {
        Completable completable = Multi.createFrom().item(1).convert().with(MultiRxConverters.toCompletable());
        assertThat(completable).isNotNull();
        completable.test().assertComplete();
    }

    @Test
    public void testThatSubscriptionOnCompletableProducesTheValue() {
        AtomicBoolean called = new AtomicBoolean();
        Completable completable = Multi.createFrom().deferred(() -> {
            called.set(true);
            return Multi.createFrom().item(2);
        }).convert().with(MultiRxConverters.toCompletable());

        assertThat(completable).isNotNull();
        assertThat(called).isFalse();
        completable.test().assertComplete();
        assertThat(called).isTrue();
    }

    @Test
    public void testCreatingACompletableFromVoid() {
        Completable completable = Multi.createFrom().item((Object) null).convert()
                .with(MultiRxConverters.toCompletable());
        assertThat(completable).isNotNull();
        completable.test().assertComplete();
    }

    @Test
    public void testCreatingACompletableWithFailure() {
        Completable completable = Multi.createFrom().failure(new IOException("boom")).convert()
                .with(MultiRxConverters.toCompletable());
        assertThat(completable).isNotNull();
        completable.test().assertError(e -> {
            assertThat(e).hasMessage("boom").isInstanceOf(IOException.class);
            return true;
        });
    }

    @Test
    public void testCreatingASingle() {
        Single<Optional<Integer>> single = Multi.createFrom().item(1).convert().with(MultiRxConverters.toSingle());
        assertThat(single).isNotNull();
        single.test()
                .assertValue(Optional.of(1))
                .assertComplete();
    }

    @Test
    public void testCreatingASingleByConverter() {
        Single<Optional<Integer>> single = Multi.createFrom().item(1).convert().with(MultiRxConverters.toSingle());
        assertThat(single).isNotNull();
        single.test()
                .assertValue(Optional.of(1))
                .assertComplete();
    }

    @Test
    public void testCreatingASingleFromNull() {
        Single<Integer> single = Multi.createFrom().item((Integer) null).convert()
                .with(MultiRxConverters.toSingle().onEmptyThrow(() -> new NoSuchElementException("not found")));
        assertThat(single).isNotNull();
        single
                .test()
                .assertFailure(NoSuchElementException.class)
                .assertNoValues();
    }

    @Test
    public void testCreatingASingleFromNullWithConverter() {
        Single<Integer> single = Multi.createFrom().item((Integer) null).convert()
                .with(MultiRxConverters.toSingle().onEmptyThrow(() -> new NoSuchElementException("not found")));
        assertThat(single).isNotNull();
        single
                .test()
                .assertFailure(NoSuchElementException.class)
                .assertNoValues();
    }

    @Test
    public void testCreatingASingleWithFailure() {
        Single<Optional<Integer>> single = Multi.createFrom().<Integer> failure(new IOException("boom")).convert()
                .with(MultiRxConverters.toSingle());
        assertThat(single).isNotNull();
        single.test().assertError(e -> {
            assertThat(e).hasMessage("boom").isInstanceOf(IOException.class);
            return true;
        });
    }

    @Test
    public void testCreatingAMaybe() {
        Maybe<Integer> maybe = Multi.createFrom().item(1).convert().with(MultiRxConverters.toMaybe());
        assertThat(maybe).isNotNull();
        maybe.test()
                .assertValue(1)
                .assertComplete();
    }

    @Test
    public void testCreatingAMaybeFromNull() {
        Maybe<Integer> maybe = Multi.createFrom().item((Integer) null).convert().with(MultiRxConverters.toMaybe());
        assertThat(maybe).isNotNull();
        maybe
                .test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void testCreatingAMaybeWithFailure() {
        Maybe<Integer> maybe = Multi.createFrom().<Integer> failure(new IOException("boom")).convert()
                .with(MultiRxConverters.toMaybe());
        assertThat(maybe).isNotNull();
        maybe.test().assertError(e -> {
            assertThat(e).hasMessage("boom").isInstanceOf(IOException.class);
            return true;
        });
    }

    @Test
    public void testCreatingAnObservable() {
        Observable<Integer> observable = Multi.createFrom().item(1).convert().with(MultiRxConverters.toObservable());
        assertThat(observable).isNotNull();
        observable.test()
                .assertValue(1)
                .assertComplete();
    }

    @Test
    public void testCreatingAnObservableFromNull() {
        Observable<Integer> observable = Multi.createFrom().item((Integer) null).convert()
                .with(MultiRxConverters.toObservable());
        assertThat(observable).isNotNull();
        observable
                .test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void testCreatingAnObservableWithFailure() {
        Observable<Integer> observable = Multi.createFrom().<Integer> failure(new IOException("boom")).convert()
                .with(MultiRxConverters.toObservable());
        assertThat(observable).isNotNull();
        observable.test().assertError(e -> {
            assertThat(e).hasMessage("boom").isInstanceOf(IOException.class);
            return true;
        });
    }

    @Test
    public void testCreatingAFlowable() {
        Flowable<Integer> flowable = Multi.createFrom().item(1).convert().with(MultiRxConverters.toFlowable());
        assertThat(flowable).isNotNull();
        flowable.test()
                .assertValue(1)
                .assertComplete();
    }

    @Test
    public void testCreatingAFlowableWithRequest() {
        AtomicBoolean called = new AtomicBoolean();
        MultiAssertSubscriber<Integer> subscriber = Multi.createFrom()
                .deferred(() -> Multi.createFrom().item(1).onItem().invoke((item) -> called.set(true)))
                .convert().with(MultiRxConverters.toFlowable())
                .subscribeWith(MultiAssertSubscriber.create(0));

        assertThat(called).isFalse();
        subscriber.assertHasNotReceivedAnyItem().assertSubscribed();
        subscriber.request(1);
        subscriber.assertCompletedSuccessfully().assertReceived(1);
        assertThat(called).isTrue();
    }

    @Test
    public void testCreatingAFlowableFromNull() {
        Flowable<Integer> flowable = Multi.createFrom().item((Integer) null).convert()
                .with(MultiRxConverters.toFlowable());
        assertThat(flowable).isNotNull();
        flowable
                .test()
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void testCreatingAFlowableWithFailure() {
        Flowable<Integer> flowable = Multi.createFrom().<Integer> failure(new IOException("boom")).convert()
                .with(MultiRxConverters.toFlowable());
        assertThat(flowable).isNotNull();
        flowable.test().assertError(e -> {
            assertThat(e).hasMessage("boom").isInstanceOf(IOException.class);
            return true;
        });
    }
}
