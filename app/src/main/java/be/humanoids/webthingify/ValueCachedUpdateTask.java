package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.mozilla.iot.webthing.Value;

class ValueCachedUpdateTask<T> extends AsyncTask<T, Void, Void> {
    private final Value<T> value;

    ValueCachedUpdateTask(@NonNull Value<T> value) {
        super();
        this.value = value;
    }

    @SafeVarargs
    @Override
    protected final Void doInBackground(T... valueUpdates) {
        for (T update : valueUpdates) {
            if (update != null) {
                value.notifyOfExternalUpdate(update);
            }
        }
        return null;
    }
}
