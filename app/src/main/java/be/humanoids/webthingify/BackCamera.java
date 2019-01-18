package be.humanoids.webthingify;

import com.androidhiddencamera.config.CameraFacing;

public class BackCamera extends Camera {
    public BackCamera() {
        super();
        facing = CameraFacing.REAR_FACING_CAMERA;
    }
}
