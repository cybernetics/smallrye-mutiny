package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;

import io.reactivex.Flowable;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.test.AssertSubscriber;

public class UniOnItemDisjointTest {

    private final Uni<String[]> array = Uni.createFrom().item(new String[] { "a", "b", "c" });
    private final Uni<Integer> failed = Uni.createFrom().failure(new IOException("boom"));

    @Test
    public void testDisjointWithArray() {
        List<String> r = array
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    public void testDisjointWithEmptyArray() {
        List<String> r = Uni.createFrom().item(new String[0])
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).isEmpty();
    }

    @Test
    public void testDisjointFromFailure() {
        assertThatThrownBy(() -> failed
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely()).hasCauseInstanceOf(IOException.class);
    }

    @Test
    public void testDisjointFromInvalidItemType() {
        assertThatThrownBy(() -> Uni.createFrom().item("hello")
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDisjointFromNull() {
        List<String> list = Uni.createFrom().nullItem()
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();

        assertThat(list).isEmpty();
    }

    @Test
    public void testDisjointWithPublisher() {
        List<String> r = Uni.createFrom().item(Flowable.just("a", "b", "c"))
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    public void testDisjointWithEmptyPublisher() {
        List<String> r = Uni.createFrom().item(Flowable.empty())
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).isEmpty();
    }

    @Test
    public void testDisjointWithMulti() {
        List<String> r = Uni.createFrom().item(Multi.createFrom().items("a", "b", "c"))
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    public void testDisjointWithEmptyMulti() {
        List<String> r = Uni.createFrom().item(Multi.createFrom().empty())
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).isEmpty();
    }

    @Test
    public void testDisjointWithIterable() {
        List<String> r = Uni.createFrom().item(Arrays.asList("a", "b", "c"))
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    public void testDisjointWithEmptyIterable() {
        List<String> r = Uni.createFrom().item(Collections.emptyList())
                .onItem().<String> disjoint()
                .collectItems().asList()
                .await().indefinitely();
        assertThat(r).isEmpty();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDisjointWithNeverPublisher() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AssertSubscriber<String> subscriber = Uni.createFrom()
                .item(Flowable.never().doOnCancel(() -> cancelled.set(true)))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(AssertSubscriber.create(10));

        subscriber.assertSubscribed()
                .assertNotTerminated();

        assertThat(cancelled).isFalse();

        subscriber.cancel();

        assertThat(cancelled).isTrue();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDisjointWithNothing() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AssertSubscriber<String> subscriber = Uni.createFrom()
                .item(Multi.createFrom().nothing().on().cancellation(() -> cancelled.set(true)))
                .onItem().<String> disjoint()
                .subscribe().withSubscriber(AssertSubscriber.create(10));

        subscriber.assertSubscribed()
                .assertNotTerminated();

        assertThat(cancelled).isFalse();

        subscriber.cancel();

        assertThat(cancelled).isTrue();
    }
}
