package io.smallrye.mutiny.operators.multi.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.smallrye.mutiny.test.AssertSubscriber;

public class EmitterBasedMultiTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatConsumerCannotBeNull() {
        Multi.createFrom().emitter(null, BackPressureStrategy.BUFFER);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThatStrategyCannotBeNull() {
        Multi.createFrom().emitter(e -> {
        }, null);
    }

    @Test
    public void testBasicEmitterBehavior() {
        AtomicBoolean terminated = new AtomicBoolean();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(e -> {
            e.onTermination(() -> terminated.set(true));
            e.emit(1).emit(2).emit(3).complete();

            e.fail(new Exception("boom-1"));
        }).subscribe().withSubscriber(AssertSubscriber.create(3));
        subscriber.assertSubscribed()
                .assertReceived(1, 2, 3)
                .assertCompletedSuccessfully();
        assertThat(terminated).isTrue();
    }

    @Test
    public void testWithConsumerThrowingException() {
        AtomicBoolean terminated = new AtomicBoolean();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(e -> {
            e.onTermination(() -> terminated.set(true));
            e.emit(1).emit(2).emit(3);

            throw new RuntimeException("boom");
        }).subscribe().withSubscriber(AssertSubscriber.create(3));
        subscriber.assertSubscribed()
                .assertReceived(1, 2, 3)
                .assertHasFailedWith(RuntimeException.class, "boom");
        assertThat(terminated).isTrue();
    }

    @Test
    public void testWithAFailure() {
        AtomicBoolean terminated = new AtomicBoolean();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(e -> {
            e.onTermination(() -> terminated.set(true));
            e.emit(1).emit(2).emit(3).fail(new Exception("boom"));

            e.fail(new Exception("boom-1"));
        }).subscribe().withSubscriber(AssertSubscriber.create(3));
        subscriber.assertSubscribed()
                .assertReceived(1, 2, 3)
                .assertHasFailedWith(Exception.class, "boom");
        assertThat(terminated).isTrue();
    }

    @Test
    public void testTerminationNotCalled() {
        AtomicBoolean terminated = new AtomicBoolean();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(e -> {
            e.onTermination(() -> terminated.set(true));
            e.emit(1).emit(2).emit(3);
        }).subscribe().withSubscriber(AssertSubscriber.create(3));
        subscriber.assertSubscribed()
                .assertReceived(1, 2, 3)
                .assertHasNotCompleted()
                .assertHasNotFailed();
        assertThat(terminated).isFalse();
        subscriber.cancel();
        assertThat(terminated).isTrue();
    }

    @Test
    public void testThatEmitterCannotEmitNull() {
        AtomicBoolean terminated = new AtomicBoolean();
        Multi.createFrom().<String> emitter(e -> {
            e.onTermination(() -> terminated.set(true));
            e.emit("a");
            e.emit(null);
        }).subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");
        assertThat(terminated).isTrue();

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.emit(null);
        }, BackPressureStrategy.LATEST)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.emit(null);
        }, BackPressureStrategy.DROP)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.emit(null);
        }, BackPressureStrategy.ERROR)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.emit(null);
        }, BackPressureStrategy.IGNORE)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

    }

    @Test
    public void testThatEmitterCannotPropagateNullAsFailure() {
        AtomicBoolean terminated = new AtomicBoolean();
        Multi.createFrom().<String> emitter(e -> {
            e.onTermination(() -> terminated.set(true));
            e.emit("a");
            e.fail(null);
        }).subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");
        assertThat(terminated).isTrue();

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.fail(null);
        }, BackPressureStrategy.LATEST)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.fail(null);
        }, BackPressureStrategy.DROP)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.fail(null);
        }, BackPressureStrategy.ERROR)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

        Multi.createFrom().<String> emitter(e -> {
            e.emit("a");
            e.fail(null);
        }, BackPressureStrategy.IGNORE)
                .subscribe().withSubscriber(AssertSubscriber.create(2))
                .await()
                .assertHasNotCompleted()
                .assertHasFailedWith(NullPointerException.class, "")
                .assertReceived("a");

    }

    @Test
    public void testSerializedWithConcurrentEmissions() {
        AtomicReference<MultiEmitter<? super Integer>> reference = new AtomicReference<>();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(reference::set).subscribe()
                .withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        await().until(() -> reference.get() != null);

        CountDownLatch latch = new CountDownLatch(2);
        Runnable r1 = () -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                // Ignore me.
            }
            for (int i = 0; i < 1000; i++) {
                reference.get().emit(i);
            }
        };

        Runnable r2 = () -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                // Ignore me.
            }
            for (int i = 0; i < 200; i++) {
                reference.get().emit(i);
            }
        };

        ExecutorService service = Executors.newFixedThreadPool(2);
        service.submit(r1);
        service.submit(r2);

        await().until(() -> subscriber.items().size() == 1200);
        service.shutdown();
    }

    @Test
    public void testSerializedWithConcurrentEmissionsAndFailure() {
        AtomicReference<MultiEmitter<? super Integer>> reference = new AtomicReference<>();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().<Integer> emitter(reference::set).subscribe()
                .withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        await().until(() -> reference.get() != null);

        CountDownLatch latch = new CountDownLatch(2);
        Runnable r1 = () -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                // Ignore me.
            }
            for (int i = 0; i < 1000; i++) {
                reference.get().emit(i);
            }
        };

        Runnable r2 = () -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                // Ignore me.
            }
            for (int i = 0; i < 200; i++) {
                reference.get().emit(i);
            }
            reference.get().fail(new Exception("boom"));
        };

        ExecutorService service = Executors.newFixedThreadPool(2);
        service.submit(r1);
        service.submit(r2);

        subscriber.await().assertHasFailedWith(Exception.class, "boom");
        service.shutdown();
    }
}
