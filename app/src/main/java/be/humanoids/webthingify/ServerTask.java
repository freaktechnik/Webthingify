package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.Utils;
import org.mozilla.iot.webthing.WebThingServer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import fi.iki.elonen.router.RouterNanoHTTPD;

class ServerTask extends AsyncTask<Thing, Void, WebThingServer> {
    private final ResultHandler delegate;
    private @Nullable
    WebThingServer server;
    private boolean shouldBeRunning = true;
    private File tempDir;

    ServerTask(@NonNull ResultHandler handler, File tempDir) {
        super();
        this.delegate = handler;
        this.tempDir = tempDir;
    }

    @Override
    protected WebThingServer doInBackground(Thing... things) {
        try {
            WebThingServer.Route camRoute = new WebThingServer.Route("/static/(.+)",
                    RouterNanoHTTPD.StaticPageHandler.class,
                    new Object[]{
                            this.tempDir
                    });
            WebThingServer server = new WebThingServer(new WebThingServer.SingleThing(things[0]),
                    8088,
                    null,
                    null,
                    Collections.singletonList(camRoute));
            server.start(false);
            for (String address : Utils.getAddresses()) {
                Log.i("wt:server", address + ":" + Integer.toString(server.getListeningPort()));
            }
            return server;
        } catch (IOException e) {
            Log.e("wt:server", "Error starting server", e);
        }
        return null;
    }

    protected void onPostExecute(@Nullable WebThingServer server) {
        this.server = server;
        if (!shouldBeRunning) {
            onDestroy();
        } else {
            this.delegate.handleResult(server != null);
        }
    }

    void onDestroy() {
        shouldBeRunning = false;
        if (server != null) {
            server.stop();
            server = null;
            Log.i("wt:server", "stopped");
        }
    }

    public interface ResultHandler {
        void handleResult(boolean isRunning);
    }
}
