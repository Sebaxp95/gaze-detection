package org.draxus.gaze;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.draxus.clm.GazeDetection;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Random;

public class CameraActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = CameraActivity.class.getName();

    private CameraBridgeViewBase mOpenCvCameraView;

    private GazeDetection gazeDetector;

    private boolean isTrained = false;

    private Point previousPoint;
    private int previousPointRepetitions = 0;
    private Button front;
    private Button back;
    private Random random = new Random(1);

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    gazeDetector = new GazeDetection(
                            Environment.getExternalStorageDirectory() + "/Gazer/model/main_ccnf_general.txt",
                            Environment.getExternalStorageDirectory() + "/Gazer/classifiers/lbpcascade_frontalface.xml",
                            Environment.getExternalStorageDirectory() + "/Gazer/weka/Manuel30");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Log.d(TAG, Build.CPU_ABI);

        setContentView(R.layout.camera_surface_view);
        mOpenCvCameraView = findViewById(R.id.camera_activity_java_surface_view);

        front = findViewById(R.id.button1);
        front.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCamera(CameraBridgeViewBase.CAMERA_ID_FRONT);
            }
        });
        back = findViewById(R.id.button2);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCamera(CameraBridgeViewBase.CAMERA_ID_BACK);
            }
        });

    }

    private void setCamera(int cameraIndex) {
        mOpenCvCameraView.setCameraIndex(cameraIndex);
        mOpenCvCameraView.setMaxFrameSize(800, 800); // TODO choose right size?
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        back.setVisibility(SurfaceView.INVISIBLE);
        front.setVisibility(SurfaceView.INVISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onTraining(Mat frame) {

        Imgproc.putText(frame, "TRAINING", new Point(20, 20), Core.FONT_HERSHEY_PLAIN, 1, new Scalar(0));

        if (previousPoint != null) {
            Log.d(TAG, "Training (" + previousPoint.x + "," + previousPoint.y + ")");
            Double[] gazeFeatures = gazeDetector.runDetection(frame);
            isTrained = gazeDetector.train(gazeFeatures, previousPoint);

            // Keep same point for a while, otherwise changes too quickly
            if (previousPointRepetitions < 15) {
                Imgproc.circle(frame, previousPoint, 10, new Scalar(0), 2);
                previousPointRepetitions++;
                return frame;
            }
        }

        previousPointRepetitions = 0;

        int x = random.nextInt(frame.cols());
        int y = random.nextInt(frame.rows());
        previousPoint = new Point(x, y);
        Imgproc.circle(frame, previousPoint, 10, new Scalar(0), 2);

        return frame;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        Mat currentFrame = inputFrame.gray();

        if (!isTrained) {
            return onTraining(currentFrame);
        }

        if (gazeDetector != null) {

            Double[] gazeFeatures = gazeDetector.runDetection(currentFrame);
            Point predictedPoint = gazeDetector.predictLocation(gazeFeatures);

            if (predictedPoint != null)
                Imgproc.circle(currentFrame, predictedPoint, 10, new Scalar(0), 2);
        }

        Imgproc.putText(currentFrame, "DETECT", new Point(20, 20), Core.FONT_HERSHEY_PLAIN, 1, new Scalar(0));

        return currentFrame;
    }
}
