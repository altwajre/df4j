package org.df4j.core.protocols;

import java.util.concurrent.Executor;

/**
 * Interrelated interfaces and static methods for establishing
 * flow-controlled components in which {@link java.util.concurrent.Flow.Publisher Publishers}
 * produce items consumed by one or more {@link java.util.concurrent.Flow.Subscriber
 * Subscribers}, each managed by a {@link java.util.concurrent.Flow.Subscription
 * Subscription}.
 *
 * <p>These interfaces correspond to the <a
 * href="http://www.reactive-streams.org/"> reactive-streams</a>
 * specification.  They apply in both concurrent and distributed
 * asynchronous settings: All (seven) methods are defined in {@code
 * void} "one-way" message style. Communication relies on a simple form
 * of flow control (method {@link java.util.concurrent.Flow.Subscription#request}) that can be
 * used to avoid resource management problems that may otherwise occur
 * in "push" based systems.
 *
 * <p><b>Examples.</b> A {@link java.util.concurrent.Flow.Publisher} usually defines its own
 * {@link java.util.concurrent.Flow.Subscription} implementation; constructing one in method
 * {@code subscribe} and issuing it to the calling {@link
 * java.util.concurrent.Flow.Subscriber}. It publishes items to the subscriber asynchronously,
 * normally using an {@link Executor}.  For example, here is a very
 * simple publisher that only issues (when requested) a single {@code
 * TRUE} item to a single subscriber.  Because the subscriber receives
 * only a single item, this class does not use buffering and ordering
 * control required in most implementations (for example {@link
 * SubmissionPublisher}).
 *
 * <pre> {@code
 * class OneShotPublisher implements Publisher<Boolean> {
 *   private final ExecutorService executor = ForkJoinPool.commonPool(); // daemon-based
 *   private boolean subscribed; // true after first subscribe
 *   public synchronized void subscribe(Subscriber<? super Boolean> subscriber) {
 *     if (subscribed)
 *       subscriber.onError(new IllegalStateException()); // only one allowed
 *     else {
 *       subscribed = true;
 *       subscriber.onSubscribe(new OneShotSubscription(subscriber, executor));
 *     }
 *   }
 *   static class OneShotSubscription implements Subscription {
 *     private final Subscriber<? super Boolean> subscriber;
 *     private final ExecutorService executor;
 *     private Future<?> future; // to allow cancellation
 *     private boolean completed;
 *     OneShotSubscription(Subscriber<? super Boolean> subscriber,
 *                         ExecutorService executor) {
 *       this.subscriber = subscriber;
 *       this.executor = executor;
 *     }
 *     public synchronized void request(long n) {
 *       if (!completed) {
 *         completed = true;
 *         if (n <= 0) {
 *           IllegalArgumentException ex = new IllegalArgumentException();
 *           executor.execute(() -> subscriber.onError(ex));
 *         } else {
 *           future = executor.submit(() -> {
 *             subscriber.onNext(Boolean.TRUE);
 *             subscriber.onComplete();
 *           });
 *         }
 *       }
 *     }
 *     public synchronized void cancel() {
 *       completed = true;
 *       if (future != null) future.cancel(false);
 *     }
 *   }
 * }}</pre>
 *
 * <p>A {@link java.util.concurrent.Flow.Subscriber} arranges that items be requested and
 * processed.  Items (invocations of {@link java.util.concurrent.Flow.Subscriber#onNext}) are
 * not issued unless requested, but multiple items may be requested.
 * Many Subscriber implementations can arrange this in the style of
 * the following example, where a buffer size of 1 single-steps, and
 * larger sizes usually allow for more efficient overlapped processing
 * with less communication; for example with a value of 64, this keeps
 * total outstanding requests between 32 and 64.
 * Because Subscriber method invocations for a given {@link
 * java.util.concurrent.Flow.Subscription} are strictly ordered, there is no need for these
 * methods to use locks or volatiles unless a Subscriber maintains
 * multiple Subscriptions (in which case it is better to instead
 * define multiple Subscribers, each with its own Subscription).
 *
 * <pre> {@code
 * class SampleSubscriber<T> implements Subscriber<T> {
 *   final Consumer<? super T> consumer;
 *   Subscription subscription;
 *   final long bufferSize;
 *   long count;
 *   SampleSubscriber(long bufferSize, Consumer<? super T> consumer) {
 *     this.bufferSize = bufferSize;
 *     this.consumer = consumer;
 *   }
 *   public void onSubscribe(Subscription subscription) {
 *     long initialRequestSize = bufferSize;
 *     count = bufferSize - bufferSize / 2; // re-request when half consumed
 *     (this.subscription = subscription).request(initialRequestSize);
 *   }
 *   public void onNext(T item) {
 *     if (--count <= 0)
 *       subscription.request(count = bufferSize - bufferSize / 2);
 *     consumer.accept(item);
 *   }
 *   public void onError(Throwable ex) { ex.printStackTrace(); }
 *   public void onComplete() {}
 * }}</pre>
 *
 * <p>The default value of {@link #defaultBufferSize} may provide a
 * useful starting point for choosing request sizes and capacities in
 * Flow components based on expected rates, resources, and usages.
 * Or, when flow control is never needed, a subscriber may initially
 * request an effectively unbounded number of items, as in:
 *
 * <pre> {@code
 * class UnboundedSubscriber<T> implements Subscriber<T> {
 *   public void onSubscribe(Subscription subscription) {
 *     subscription.request(Long.MAX_VALUE); // effectively unbounded
 *   }
 *   public void onNext(T item) { use(item); }
 *   public void onError(Throwable ex) { ex.printStackTrace(); }
 *   public void onComplete() {}
 *   void use(T item) { ... }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 9
 */
public class Flow {

    private Flow() {} // uninstantiable


    /**
     * A producer of items (and related control messages) received by
     * Subscribers.  Each current {@link Subscriber} receives the same
     * items (via method {@code onNext}) in the same order, unless
     * drops or errors are encountered. If a Publisher encounters an
     * error that does not allow items to be issued to a Subscriber,
     * that Subscriber receives {@code onError}, and then receives no
     * further messages.  Otherwise, when it is known that no further
     * messages will be issued to it, a subscriber receives {@code
     * onComplete}.  Publishers ensure that Subscriber method
     * invocations for each subscription are strictly ordered in <a
     * href="package-summary.html#MemoryVisibility"><i>happens-before</i></a>
     * order.
     *
     * <p>Publishers may vary in policy about whether drops (failures
     * to issue an item because of resource limitations) are treated
     * as unrecoverable errors.  Publishers may also vary about
     * whether Subscribers receive items that were produced or
     * available before they subscribed.
     *
     * @param <T> the published item type
     */
    public static interface Publisher<T> {
        /**
         * Adds the given Subscriber if possible.  If already
         * subscribed, or the attempt to subscribe fails due to policy
         * violations or errors, the Subscriber's {@code onError}
         * method is invoked with an {@link IllegalStateException}.
         * Otherwise, the Subscriber's {@code onSubscribe} method is
         * invoked with a new {@link Subscription}.  Subscribers may
         * enable receiving items by invoking the {@code request}
         * method of this Subscription, and may unsubscribe by
         * invoking its {@code cancel} method.
         *
         * @param subscriber the subscriber
         * @throws NullPointerException if subscriber is null
         */
        void subscribe(Subscriber<? super T> subscriber);

        default void subscribe(Scalar.Subscriber<? super T> subscriber){
            Scalar2FlowSubscriber proxySubscriber = new Scalar2FlowSubscriber(subscriber);
            subscribe(proxySubscriber);
        }

        default void subscribe(Flood.Subscriber<? super T> subscriber) {
            Flood2FlowSubscriber proxySubscriber = new Flood2FlowSubscriber(subscriber);
            subscribe(proxySubscriber);
        }
    }

    /**
     * A receiver of messages.  The methods in this interface are
     * invoked in strict sequential order for each {@link
     * Subscription}.
     *
     * @param <T> the subscribed item type
     */
    public static interface Subscriber<T> extends Flood.Subscriber<T> {

        @Override
        default void onSubscribe(Disposable s) {
            throw new UnsupportedOperationException();
        }

        /**
         * Method invoked prior to invoking any other Subscriber
         * methods for the given Subscription. If this method throws
         * an exception, resulting behavior is not guaranteed, but may
         * cause the Subscription not to be established or to be cancelled.
         *
         * <p>Typically, implementations of this method invoke {@code
         * subscription.request} to enable receiving items.
         *
         * @param subscription a new subscription
         */
        public void onSubscribe(Subscription subscription);
    }

    /**
     * Message control linking a {@link Publisher} and {@link
     * Subscriber}.  Subscribers receive items only when requested,
     * and may cancel at any time. The methods in this interface are
     * intended to be invoked only by their Subscribers; usages in
     * other contexts have undefined effects.
     */
    public static interface Subscription {
        /**
         * Adds the given number {@code n} of items to the current
         * unfulfilled demand for this subscription.  If {@code n} is
         * less than or equal to zero, the Subscriber will receive an
         * {@code onError} signal with an {@link
         * IllegalArgumentException} argument.  Otherwise, the
         * Subscriber will receive up to {@code n} additional {@code
         * onNext} invocations (or fewer if terminated).
         *
         * @param n the increment of demand; a value of {@code
         * Long.MAX_VALUE} may be considered as effectively unbounded
         */
        public void request(long n);

        /**
         * Causes the Subscriber to (eventually) stop receiving
         * messages.  Implementation is best-effort -- additional
         * messages may be received after invoking this method.
         * A cancelled subscription need not ever receive an
         * {@code onComplete} or {@code onError} signal.
         */
        void cancel();

        boolean isCancelled();
    }

    /**
     * A component that acts as both a Subscriber and Publisher.
     *
     * @param <T> the subscribed item type
     * @param <R> the published item type
     */
    public static interface Processor<T,R> extends Subscriber<T>, Publisher<R> {
    }

    /**
     *
     * converts Scalar.Subscriber to a Flow.Subscriber
     *
     * @param <T>
     */
    public static class Scalar2FlowSubscriber<T> implements Subscriber<T>, Disposable {
        private final Scalar.Subscriber<T> subscriber;
        private Subscription subscription;

        public Scalar2FlowSubscriber(Scalar.Subscriber<T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            subscriber.onSubscribe(this);
            s.request(1);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onSuccess(null);
            subscription.cancel();
        }

        @Override
        public void onNext(T token) {
            subscriber.onSuccess(token);
            subscription.cancel();
        }

        @Override
        public void dispose() {
            subscription.cancel();
        }

        @Override
        public boolean isDisposed() {
            return subscription.isCancelled();
        }
    }

    public static class Flood2FlowSubscriber<T> implements Subscriber<T>, Disposable {
        private final Flood.Subscriber<T> subscriber;
        private Subscription subscription;

        public Flood2FlowSubscriber(Flood.Subscriber<T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            subscriber.onSubscribe(this);
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onNext(T token) {
            subscriber.onNext(token);
        }

        @Override
        public void dispose() {
            subscription.cancel();
        }

        @Override
        public boolean isDisposed() {
            return subscription.isCancelled();
        }
    }
}