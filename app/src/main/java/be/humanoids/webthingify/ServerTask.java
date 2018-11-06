package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.WebThingServer;

import java.io.IOException;

class ServerTask extends AsyncTask<Thing, Void, WebThingServer> {
    private @Nullable WebThingServer server;
    private final ResultHandler delegate;
    private boolean shouldBeRunning = true;

    public interface ResultHandler {
        void handleResult(boolean isRunning);
    }

    ServerTask(@NonNull ResultHandler handler) {
        super();
        this.delegate = handler;
    }

    @Override
    protected WebThingServer doInBackground(Thing... things) {
        try {
            WebThingServer server = new WebThingServer(new WebThingServer.SingleThing(things[0]), 8088);
            server.start(false);
            Log.i("wt:server", Integer.toString(server.getListeningPort()));
            return server;
        } catch (IOException e) {
            Log.e("wt:server", "Error starting server", e);
        }
        return null;
    }

    protected void onPostExecute(@Nullable WebThingServer server) {
        this.server = server;
        if(!shouldBeRunning) {
            onDestroy();
        }
        else {
            this.delegate.handleResult(server != null);
        }
    }

    void onDestroy() {
        shouldBeRunning = false;
        if(server != null) {
            server.stop();
            server = null;
            Log.i("wt:server", "stopped");
        }
    }
}
