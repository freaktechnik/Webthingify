package be.humanoids.webthingify;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.WebThingServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

class ServerTask extends AsyncTask<Thing, Void, WebThingServer> {
    private final ResultHandler delegate;
    private @Nullable
    WebThingServer server;
    private boolean shouldBeRunning = true;

    ServerTask(@NonNull ResultHandler handler) {
        super();
        this.delegate = handler;
    }

    @Override
    protected WebThingServer doInBackground(Thing... things) {
        try {
            String ipAddress = null;
            NetworkInterface netInterface;
            InetAddress address;
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
                netInterface = interfaces.nextElement();
                for (Enumeration<InetAddress> addresses = netInterface.getInetAddresses(); addresses.hasMoreElements(); ) {
                    address = addresses.nextElement();
                    if (!address.isLoopbackAddress()) {
                        ipAddress = address.getHostAddress();
                    }
                }
            }
            WebThingServer server = new WebThingServer(new WebThingServer.SingleThing(things[0]), 8088, ipAddress);
            server.start(false);
            Log.i("wt:server", ipAddress + ":" + Integer.toString(server.getListeningPort()));
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
