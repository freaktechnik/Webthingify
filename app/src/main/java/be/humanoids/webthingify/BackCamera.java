package be.humanoids.webthingify;

import android.view.Surface;

import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraRotation;

public class BackCamera extends Camera {
    public BackCamera() {
        super();
        facing = CameraFacing.REAR_FACING_CAMERA;
    }

    @Override
    protected int getOrientation() {
        switch (currentRotation())
        {
            case Surface.ROTATION_0:
                return CameraRotation.ROTATION_90;
            case Surface.ROTATION_90:
                return CameraRotation.ROTATION_0;
            case Surface.ROTATION_180:
                return CameraRotation.ROTATION_270;
            default:
                return CameraRotation.ROTATION_180;
        }
    }
}
