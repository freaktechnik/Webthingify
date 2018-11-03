package be.humanoids.webthingify;

import org.json.JSONObject;
import org.mozilla.iot.webthing.Action;

import java.util.UUID;

class VibrateAction extends Action {
    public VibrateAction(Phone thing, JSONObject input) {
        super(UUID.randomUUID().toString(), thing, "vibrate", input);
    }

    @Override
    public void performAction() {
        ((Phone) getThing()).vibrate();
    }
}
