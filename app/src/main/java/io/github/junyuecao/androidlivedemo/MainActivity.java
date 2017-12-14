package io.github.junyuecao.androidlivedemo;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements CameraCapture.PreviewCallback,
        CameraCapture.SurfaceTextureCallback, View.OnClickListener {
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private GLSurfaceView mGlSurfaceView;
    private Button mSwitchCamera;

    private CameraCapture mCameraCapture;
    private int mFilter = TextureRender.FILTER_NONE;
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.switchCamera:
                mCameraCapture.switchCamera();
                break;
            case R.id.filter:
                mFilter = 1 - mFilter;
                mCameraCapture.setFilter(mFilter);
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGlSurfaceView = findViewById(R.id.surfaceView);
        mCameraCapture = new CameraCapture(mGlSurfaceView);
        mCameraCapture.setPreviewCallback(this);
        mCameraCapture.setSurfaceTextureCallback(this);
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setRenderer(mCameraCapture);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mSwitchCamera = findViewById(R.id.switchCamera);
        mSwitchCamera.setOnClickListener(this);
        findViewById(R.id.filter).setOnClickListener(this);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void onPreviewFrame(byte[] bytes, int width, int height, int rotation, int fmt, long tsInNanoTime) {

    }

    @Override
    public void onSurfaceCreated(int texWidth, int texHeight) {

    }

    @Override
    public int processTexture(int texId, int texWidth, int texHeight, float[] matrix, byte[] outYUV) {
        return texId;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraCapture.onResume();
        mGlSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGlSurfaceView.onPause();
        mCameraCapture.onPause();
    }


}
