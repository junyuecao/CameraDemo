package io.github.junyuecao.androidlivedemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 相机，麦克风采集数据；
 * 编码器
 */
@RequiresApi(14)
public class CameraCapture implements GLSurfaceView.Renderer {
    private final String TAG = getClass().getSimpleName();


    private int targetWidth = 720;
    private int targetHeight = 1280;
    private TextureRender mGLRender;

    public interface SurfaceTextureCallback {
        void onSurfaceCreated(int texWidth, int texHeight);

        int processTexture(int texId, int texWidth, int texHeight, float[] matrix, byte[] outYUV);
    }

    public void setSurfaceTextureCallback(SurfaceTextureCallback mSurfaceTextureCallback) {
        this.mSurfaceTextureCallback = mSurfaceTextureCallback;
    }

    protected SurfaceTextureCallback mSurfaceTextureCallback;

    public interface PreviewCallback {
        void onPreviewFrame(byte[] bytes, int width, int height, int rotation, int fmt, long tsInNanoTime);
    }

    public void setPreviewCallback(PreviewCallback mPreviewCallback) {
        this.mPreviewCallback = mPreviewCallback;
    }

    protected PreviewCallback mPreviewCallback;

    /**
     * SurfaceTexure texture id
     */
    private int mTextureId = OpenGLUtils.NO_TEXTURE;
    private CameraProxy mCameraProxy;
    /**
     * 相机摄像头ID
     */
    private int mCameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private int mCameraRotation = 0;
    private int mImageFormat = ImageFormat.NV21;
    /**
     * Camera支持的分辨率
     */
    private List<Camera.Size> mSupportedPreviewSizes;

    private int mImageWidth;
    private int mImageHeight;
    private int mSurfaceHeight;
    private int mSurfaceWidth;
    /**
     * camera是否正在切换
     */
    private boolean mCameraChanging = false;
    private GLSurfaceView mGlSurfaceView;
    private SurfaceTexture mSurfaceTexture;
    /**
     * activity是否是pause状态
     */
    private boolean isPaused;

    private ByteBuffer preprocessByteBuffer;

    private long renderFrameCountTotal;

    private long lastGetFpsTime;


    public void setTargetResolution(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
    }

    public CameraCapture(GLSurfaceView glSurfaceView) {
        mTextureId = -1;
        mGlSurfaceView = glSurfaceView;
        mCameraProxy = new CameraProxy();
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        deleteCameraPreviewTexture();
        if (Camera.getNumberOfCameras() == 1
                || mCameraChanging) {
            return;
        }
        mCameraID = 1 - mCameraID;
        mCameraChanging = true;
        mCameraProxy.openCamera(mCameraID);
        setUpCamera();
        mCameraChanging = false;
        mGlSurfaceView.requestRender();
    }

    /**
     * 开/关闪光灯
     *
     * @param enable true开启 false关闭
     */
    public void enableFlashLight(final boolean enable) {
        mGlSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mCameraProxy.setLightEnable(enable);
            }
        });
    }

    /**
     * @return 当前camera闪光灯是否可用
     */
    public boolean isLightAvailable() {
        return mCameraProxy.isLightAvailable();
    }

    private void setUpCamera() {
        // 初始化Camera设备预览需要的显示区域(mSurfaceTexture)
        if (mTextureId == OpenGLUtils.NO_TEXTURE) {
            //创建一个纹理
            mTextureId = mGLRender.getTextureId();
            mSurfaceTexture = new SurfaceTexture(mTextureId);
        }
        if (mSupportedPreviewSizes == null) {
            mSupportedPreviewSizes = mCameraProxy.getSupportedPreviewSize();
        }
        //取出相机采集支持的分辨率
        try {
            Camera.Size cameraSize = CameraProxy.getClosestSupportedSize(mSupportedPreviewSizes, targetHeight, targetWidth);
            if (cameraSize != null) {
                mImageHeight = cameraSize.width;
                mImageWidth = cameraSize.height;
            } else {
                mImageHeight = targetHeight;
                mImageWidth = targetWidth;
            }

            mCameraProxy.setPreviewSize(mImageHeight, mImageWidth);
            try {
                int[] range = new int[2];
                mCameraProxy.getParameters().getPreviewFpsRange(range);
                Log.d(TAG,"FPS Range = " +range[0]+"~"+range[1]);
                mCameraProxy.getParameters().setPreviewFpsRange(Math.max(range[0],5*1000),Math.min(range[1],30*1000));
                Log.d(TAG,"Set Range = " +Math.max(range[0],5*1000)+"~"+Math.min(range[1],30*1000));
            }catch (Exception e){
                Log.e(TAG, e.getMessage(), e);
            }
            Log.d(TAG, "Camera: " + mCameraProxy.getCamera() + "Preview :" + mCameraProxy.getPreviewSize().width
                    + " height:" + mCameraProxy.getPreviewSize().height);

            final int previewBufferSize = (mImageWidth * mImageHeight * ImageFormat.getBitsPerPixel(mImageFormat)) / 8;
            if (previewCallback != null) {
                mCameraProxy.getCamera().addCallbackBuffer(new byte[previewBufferSize]);
                mCameraProxy.startPreview(mSurfaceTexture, previewCallback);
            } else
                mCameraProxy.startPreview(mSurfaceTexture, null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, info);

        mCameraRotation = info.orientation;
        if (mGLRender != null)
            mGLRender.adjustTextureBuffer(mCameraRotation, true, mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    public void setCameraDisplayOrientation(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();

        android.hardware.Camera.CameraInfo camInfo =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(mCameraID, camInfo);


        Display display = ((WindowManager) mGlSurfaceView.getContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        int rotation = display.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (camInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (camInfo.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public void onResume() {
        isPaused = false;
        if (mCameraProxy.getCamera() == null) {
            if (mCameraProxy.getNumberOfCameras() == 1) {
                mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
            mCameraProxy.openCamera(mCameraID);
            mSupportedPreviewSizes = mCameraProxy.getSupportedPreviewSize();
        }
//        mGlSurfaceView.onResume();
        mGlSurfaceView.forceLayout();
        mGlSurfaceView.requestRender();
    }

    public void onPause() {
        isPaused = true;
        mCameraProxy.releaseCamera();
//
        deleteCameraPreviewTexture();
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }

    public void onDestroy() {
    }

    private void deleteCameraPreviewTexture() {
        if (mTextureId != OpenGLUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{
                    mTextureId
            }, 0);
        }
        mTextureId = OpenGLUtils.NO_TEXTURE;
    }


    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        lastGetFpsTime = System.currentTimeMillis();
        renderFrameCountTotal = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int surfaceWidth, int surfaceHeight) {
        Log.d(TAG, "onSurfaceChanged " + surfaceWidth + "x" + surfaceHeight);
        mSurfaceWidth = surfaceWidth;
        mSurfaceHeight = surfaceHeight;
        GLES20.glEnable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);
        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        mGLRender = new TextureRender();
        mGLRender.surfaceCreated();// 需要先创建一个TextureId

        if (mCameraProxy.getCamera() != null) {
            setUpCamera();
        }
        if (mSurfaceTextureCallback != null)
            mSurfaceTextureCallback.onSurfaceCreated(mImageWidth, mImageHeight);

        mGLRender.adjustTextureBuffer(mCameraRotation, true, mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT);
        mGLRender.calculateVertexBuffer(mSurfaceWidth, mSurfaceHeight, mImageWidth, mImageHeight);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (isPaused) return;
        if (mCameraChanging) return;


        if (mCameraProxy.getCamera() == null) return;
        if (mSurfaceTexture == null) return;
        try {
            mSurfaceTexture.updateTexImage();
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        long renderStartTime = System.currentTimeMillis();
        int texId2D = mTextureId;

        if (mSurfaceTextureCallback != null) {
            float[] matrix = new float[16];
            mSurfaceTexture.getTransformMatrix(matrix);
            texId2D = mSurfaceTextureCallback.processTexture(texId2D, mImageWidth, mImageHeight, matrix, null);
        }

//        saveTextureToFile(texId2D,mImageWidth,mImageHeight);
        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        mGLRender.drawFrame(mSurfaceTexture);
        renderFrameCountTotal++;
    }

    public void saveTextureToFile(int textureId, int width, int height) {
        if (width <= 0 || height <= 0)
            return;
        ByteBuffer rgbaBuffer = ByteBuffer.allocate(width * height * 4);
        Bitmap srcBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        rgbaBuffer.position(0);
        srcBitmap.copyPixelsFromBuffer(rgbaBuffer);
        File file = new File(Environment.getExternalStorageDirectory() + File.separator + "test" + File.separator + System.currentTimeMillis() + ".jpg");
        File parent = new File(file.getParent());
        if (!parent.exists())
            parent.mkdirs();
        if (!file.exists())
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file));
            srcBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (bos != null)
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        srcBitmap.recycle();
    }
//
//    @Override
//    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//        if (!mCameraChanging) {
//            mGlSurfaceView.requestRender();
//        }
//    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (camera != null)
                camera.addCallbackBuffer(data);
            if (!mCameraChanging) {
                mGlSurfaceView.requestRender();
            }
        }
    };

    public boolean isFrontCamera() {
        return mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    public int getCameraRotation() {
        return mCameraRotation;
    }

    public void setPreprocessByteBuffer(ByteBuffer byteBuffer) {
        preprocessByteBuffer = byteBuffer;
    }

    public double getRenderFps() {
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastGetFpsTime) == 0) return 0;

        double result = (double) (renderFrameCountTotal) * 1000 / (double) (currentTime - lastGetFpsTime);
        lastGetFpsTime = currentTime;
        renderFrameCountTotal = 0;
        return result;
    }
}