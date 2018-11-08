package be.humanoids.webthingify;

import java.util.function.Consumer;

class Value<T> extends org.mozilla.iot.webthing.Value<T> {
    public Value(final T initialValue) {
        super(initialValue);
    }

    public Value(final T initialValue, final Consumer<T> valueForwarder) {
        super(initialValue, valueForwarder);
    }

    void setRemote(T newValue) {
        final ValueUpdateTask<T> task = new ValueUpdateTask<>(this);
        task.execute(newValue);
    }

    void updateRemote(T newValue) {
        final ValueCachedUpdateTask<T> task = new ValueCachedUpdateTask<>(this);
        task.execute(newValue);
    }
}
