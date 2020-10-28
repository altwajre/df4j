package org.df4j.core.actor;

import org.df4j.core.port.InpFlood;
import org.df4j.protocol.OutMessagePort;

/**
 * Actor according to Carl Hewitt
 * Has a predefined input port.
 * Additional ports can be added, though.
 * @param <T> Type of input messages
 */
public abstract class ClassicActor<T> extends Actor implements OutMessagePort<T> {
    protected InpFlood<T> inp = new InpFlood<>(this);
    private volatile MessageAction<T> nextMessageAction = this::runAction;

    public void onNext(T message) {
        inp.onNext(message);
    }

    public void onComplete() {
        inp.onComplete();
    }

    public void onError(Throwable t) {
        inp.onError(t);
    }

    @Override
    protected void runAction() throws Throwable {
        if (inp.isCompleted()) {
            Throwable cause = inp.getCompletionException();
            if (cause == null) {
                complete();
            } else {
                complete(cause);
            }
            return;
        }
        T token = inp.remove();
        this.nextMessageAction.runAction(token);
    }

    protected void nextMessageAction(MessageAction<T> messageAction) {
        this.nextMessageAction = messageAction;
    }

    protected abstract void runAction(T message) throws Throwable;

    @FunctionalInterface
    public interface MessageAction<T> {
        void runAction(T message) throws Throwable;
    }
}
