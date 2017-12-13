package io.github.junyuecao.androidlivedemo;

import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 相机开启，参数设置，闪光灯等操作
 */
public class CameraProxy {

    private final String TAG = getClass().getSimpleName();

    private int mCameraId;
    private Camera mCamera;

    private CameraInfo mCameraInfo = new CameraInfo();

    public Camera getCamera() {
        return mCamera;
    }

    public boolean openCamera(int cameraId) {
        try {
            releaseCamera();
            mCamera = Camera.open(cameraId);
            mCamera.getParameters();
            mCameraId = cameraId;
            mCamera.getCameraInfo(cameraId, mCameraInfo);

            setDefaultParameters();
        } catch (Exception e) {
            mCamera = null;
            Log.d(TAG, "openCamera fail msg=" + e.getMessage());
            return false;
        }
        Log.d(TAG, "openCamera success. camera id = " + cameraId);

        return true;
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "camera released");
        }
    }

    /**
     * 启动相机预览
     *
     * @param surfaceTexture  硬编：纹理
     * @param previewcallback 软编：YUV格式
     */
    public void startPreview(SurfaceTexture surfaceTexture, PreviewCallback previewcallback) {
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            if (previewcallback != null) {
                mCamera.setPreviewCallbackWithBuffer(previewcallback);
            }
            mCamera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public void startPreview() {
        if (mCamera != null)
            mCamera.startPreview();
    }

    public void stopPreview() {
        if (mCamera != null)
            mCamera.stopPreview();
    }

    public Size getPreviewSize() {
        if (mCamera != null) {
            return mCamera.getParameters().getPreviewSize();
        }
        return null;
    }

    public void setOneShotPreviewCallback(PreviewCallback callback) {
        mCamera.setOneShotPreviewCallback(callback);
    }

    public void addPreviewCallbackBuffer(byte[] callbackBuffer) {
        mCamera.addCallbackBuffer(callbackBuffer);
    }


    public int getOrientation() {
        if (mCameraInfo == null) {
            return 0;
        }
        return mCameraInfo.orientation;
    }

    public boolean isFlipHorizontal() {
        if (mCameraInfo == null) {
            return false;
        }
        return mCameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT ? true : false;
    }

    public int getCameraId() {
        return mCameraId;
    }

    public boolean isFrontCamera() {
        return mCameraId == CameraInfo.CAMERA_FACING_FRONT;
    }

    public void setRotation(int rotation) {
        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            params.setRotation(rotation);
            mCamera.setParameters(params);
        }
    }

    public void takePicture(Camera.ShutterCallback shutterCallback, Camera.PictureCallback rawCallback,
                            Camera.PictureCallback jpegCallback) {
        if (mCamera != null) {
            mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    public int getDisplayOrientation(int dir) {
        /**
         * 请注意前置摄像头与后置摄像头旋转定义不同
         * 请注意不同手机摄像头旋转定义不同
         */
        int newdir = dir;
        if (isFrontCamera() &&
                ((mCameraInfo.orientation == 270 && (dir & 1) == 1) ||
                        (mCameraInfo.orientation == 90 && (dir & 1) == 0))) {
            newdir = (dir ^ 2);
        }
        return newdir;
    }

    public boolean needMirror() {
        if (isFrontCamera()) {
            return true;
        } else {
            return false;
        }
    }

    private void setDefaultParameters() {
        Parameters parameters = mCamera.getParameters();
        Log.d(TAG, "parameters: " + parameters.flatten());
        if (parameters.getSupportedFocusModes().contains(
                Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes != null && flashModes.contains(Parameters.FLASH_MODE_OFF)) {
            parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
        }

        Point previewSize = getSuitablePreviewSize();
        //	parameters.setPreviewSize(previewSize.x, previewSize.y);
        parameters.setPreviewSize(640, 480);
        Point pictureSize = getSuitablePictureSize();
        parameters.setPictureSize(pictureSize.x, pictureSize.y);
//        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera.setParameters(parameters);
    }

    public Parameters getParameters() {
        return mCamera.getParameters();
    }

    public void setPreviewSize(int width, int height) {
        if (mCamera == null) {
            Log.d(TAG, "Camera is null, setPreviewSize just return");
            return;
        }
        Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(width, height);
        mCamera.setParameters(parameters);
        Log.d(TAG, mCamera.getParameters().get("preview-size"));
    }

    private Point getSuitablePreviewSize() {
        Point defaultsize = new Point(1920, 1080);
        if (mCamera != null) {
            List<Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
            for (Size s : sizes) {
                if ((s.width == defaultsize.x) && (s.height == defaultsize.y)) {
                    return defaultsize;
                }
            }
            return new Point(640, 480);
        }
        return null;
    }

    public List<Size> getSupportedPreviewSize() {
        if (mCamera != null) {
            return mCamera.getParameters().getSupportedPreviewSizes();
        }
        return null;
    }

    private Point getSuitablePictureSize() {
        Point defaultsize = new Point(4608, 3456);
        //	Point defaultsize = new Point(3264, 2448);
        if (mCamera != null) {
            Point maxSize = new Point(0, 0);
            List<Size> sizes = mCamera.getParameters().getSupportedPictureSizes();
            for (Size s : sizes) {
                if ((s.width == defaultsize.x) && (s.height == defaultsize.y)) {
                    return defaultsize;
                }
                if (maxSize.x < s.width) {
                    maxSize.x = s.width;
                    maxSize.y = s.height;
                }
            }
            return maxSize;
        }
        return null;
    }


    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    public void setLightEnable(boolean enable) {
        if (mCamera == null) return;
        Parameters parameters = mCamera.getParameters();

        if (enable) {
            parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
        } else {
            parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
        }
        try {
            mCamera.setParameters(parameters);
        } catch (Exception ignore) {
        }
    }

    public boolean isLightAvailable() {
        if (mCamera == null) return false;
        Parameters parameters = mCamera.getParameters();

        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes == null) return false;
        if (flashModes.contains(Parameters.FLASH_MODE_TORCH)) return true;
        return false;
    }

    public static Size getClosestSupportedSize(List<Size> supportedSizes, final int requestedWidth, final int requestedHeight) {
        if (supportedSizes == null) return null;
        return Collections.min(supportedSizes, new Comparator<Size>() {

            private int diff(final Size size) {
                return Math.abs(requestedWidth - size.width) + Math.abs(requestedHeight - size.height);
            }

            @Override
            public int compare(final Size lhs, final Size rhs) {
                return diff(lhs) - diff(rhs);
            }
        });
    }

}
