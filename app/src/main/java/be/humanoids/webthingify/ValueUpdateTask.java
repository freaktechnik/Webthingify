package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.mozilla.iot.webthing.Value;

class ValueUpdateTask<T> extends AsyncTask<T, Void, Void> {
    private final Value<T> value;

    ValueUpdateTask(@NonNull Value<T> value) {
        super();
        this.value = value;
    }

    public Void doInBackground(T... valueUpdates) {
        for(T update : valueUpdates) {
            if (update != null) {
                value.set(update);
            }
        }
        return null;
    }
}
