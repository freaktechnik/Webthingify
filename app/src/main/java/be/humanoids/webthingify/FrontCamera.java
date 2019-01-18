package be.humanoids.webthingify;

import com.androidhiddencamera.config.CameraFacing;

public class FrontCamera extends Camera {
    public FrontCamera() {
        super();
        facing = CameraFacing.FRONT_FACING_CAMERA;
    }
}
