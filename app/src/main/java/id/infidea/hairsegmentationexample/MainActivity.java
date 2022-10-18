// Copyright 2019 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package id.infidea.hairsegmentationexample;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.ClassificationProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketGetter;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/** Main activity of MediaPipe basic app. */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  // Flips the camera-preview frames vertically by default, before sending them into FrameProcessor
  // to be processed in a MediaPipe graph, and flips the processed frames back when they are
  // displayed. This maybe needed because OpenGL represents images assuming the image origin is at
  // the bottom-left corner, whereas MediaPipe in general assumes the image origin is at the
  // top-left corner.
  // NOTE: use "flipFramesVertically" in manifest metadata to override this behavior.
  private static final boolean FLIP_FRAMES_VERTICALLY = true;

  // Number of output frames allocated in ExternalTextureConverter.
  // NOTE: use "converterNumBuffers" in manifest metadata to override number of buffers. For
  // example, when there is a FlowLimiterCalculator in the graph, number of buffers should be at
  // least `max_in_flight + max_in_queue + 1` (where max_in_flight and max_in_queue are used in
  // FlowLimiterCalculator options). That's because we need buffers for all the frames that are in
  // flight/queue plus one for the next frame from the camera.
  private static final int NUM_BUFFERS = 2;

  static {
    // Load all native libraries needed by the app.
    System.loadLibrary("mediapipe_jni");
    try {
      System.loadLibrary("opencv_java3");
    } catch (UnsatisfiedLinkError e) {
      // Some example apps (e.g. template matching) require OpenCV 4.
      System.loadLibrary("opencv_java4");
    }
  }

  // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
  // frames onto a {@link Surface}.
  protected FrameProcessor processor;
  // Handles camera access via the {@link CameraX} Jetpack support library.
  protected CameraXPreviewHelper cameraHelper;
  FloatingActionButton actionButton;
  FrameLayout frameLayout;
  Bitmap bitmap;

  // {@link SurfaceTexture} where the camera-preview frames can be accessed.
  private SurfaceTexture previewFrameTexture;
  // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
  private SurfaceView previewDisplayView;

  // Creates and manages an {@link EGLContext}.
  private EglManager eglManager;
  ViewGroup viewGroup;
  // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
  // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
  private ExternalTextureConverter converter;

  // ApplicationInfo for retrieving metadata defined in the manifest.
  private ApplicationInfo applicationInfo;

//  private static final String INPUT_NUM_FACES_SIDE_PACKET_NAME = "num_faces";
  private static final String OUTPUT_LANDMARKS_STREAM_NAME = "output_size";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getContentViewLayoutResId());
    Log.wtf("cek","keluar");
    frameLayout = findViewById(R.id.preview_display_layout);
    frameLayout.setBackground(ContextCompat.getDrawable(this,R.drawable.photo));
    try {
      applicationInfo =
          getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Cannot find application info: " + e);
    }

    previewDisplayView = new SurfaceView(this);
    setupPreviewDisplayView();

    // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
    // binary graphs.
    AndroidAssetUtil.initializeNativeAssetManager(this);
    eglManager = new EglManager(null);
    processor =
        new FrameProcessor(
            this,
            eglManager.getNativeContext(),
            applicationInfo.metaData.getString("binaryGraphName"),
            applicationInfo.metaData.getString("inputVideoStreamName"),
            applicationInfo.metaData.getString("outputVideoStreamName"));
//    Log.e("processor",""+processor.getGraph().getCalculatorGraphConfig());
    Log.e("eglManager",""+eglManager.getNativeContext());
    processor
        .getVideoSurfaceOutput()
        .setFlipY(
            applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));

    PermissionHelper.checkAndRequestCameraPermissions(this);

//    AndroidPacketCreator packetCreator = processor.getPacketCreator();
//    Map<String, Packet> inputSidePackets = new HashMap<>();
//    inputSidePackets.put("num_faces", packetCreator.createInt32(1));
//    processor.setInputSidePackets(inputSidePackets);

//    if (Log.isLoggable(TAG, Log.VERBOSE)) {
//    processor.getGraph().addPacketCallback("output_size", (packet) -> {
//      Log.v(TAG, "Received: " + PacketGetter.getInt32(packet));
//    });
    processor.addPacketCallback(
            "output_mask_size",
            (packet) -> {
              Log.v(TAG, "Received atas: " + Arrays.toString(PacketGetter.getInt32Vector(packet)));
            });
    processor.addPacketCallback(
            "output_rgba",
            (packet) -> {
              bitmap = AndroidPacketGetter.getBitmapFromRgba(packet);
              ByteArrayOutputStream stream = new ByteArrayOutputStream();
//              bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
//              Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
//              final int color = newBitmap.getPixel(0, 0);
//              byte[] byteArray = stream.toByteArray();
//              bitmap.recycle();
              int width = bitmap.getWidth();
              int height = bitmap.getHeight();

              int size = bitmap.getRowBytes() * bitmap.getHeight();
              ByteBuffer byteBuffer = ByteBuffer.allocate(size);
              bitmap.copyPixelsToBuffer(byteBuffer);
              byte[] byteArray = byteBuffer.array();
              Log.v(TAG, "Received bitmap1: " + Arrays.toString(byteArray));
//
//              Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
//                @RequiresApi(api = Build.VERSION_CODES.O)
//                public void onGenerated(Palette p) {
//                  // Use generated instance
//                  Log.v(TAG, "Received bitmap: " + p.getSwatches());
//                }
//              });
            });
      processor.addPacketCallback(
              "output_size",
              (packet) -> {
                Log.v(TAG, "Received multi face landmarks packet: " + packet.getTimestamp());
                Log.v(TAG, "Received: " + Arrays.toString(PacketGetter.getInt32Vector(packet)));

//                PacketGetter.PacketPair multiFaceLandmarks =
//                        PacketGetter.getPairOfPackets(packet);
////
//                  Log.e("Received getPacketCreator", "" + multiFaceLandmarks);
//                Log.e("getPacket", "" + PacketGetter.getVideoHeaderWidth(packet));
//                }
//              Log.v(
//                      "zombie",
//                      "[TS:"
//                              + packet.getTimestamp()
//                              + "] "
//                              + getMultiFaceLandmarksDebugString(multiFaceLandmarks));
              });
    }
//  }

  // Used to obtain the content view for this application. If you are extending this class, and
  // have a custom layout, override this method and return the custom layout.
  protected int getContentViewLayoutResId() {
    return R.layout.activity_main;
  }

  @Override
  protected void onResume() {
    super.onResume();
    converter =
        new ExternalTextureConverter(
            eglManager.getContext(),
            applicationInfo.metaData.getInt("converterNumBuffers", NUM_BUFFERS));
    converter.setFlipY(
        applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));
    converter.setConsumer(processor);
    if (PermissionHelper.cameraPermissionsGranted(this)) {
      startCamera();

//      previewFrameTexture = converter.getSurfaceTexture();
//      previewDisplayView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    converter.close();

    // Hide preview display until we re-open the camera again.
    previewDisplayView.setVisibility(View.GONE);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  protected void onCameraStarted(SurfaceTexture surfaceTexture) {
    previewFrameTexture = surfaceTexture;
    // Make the display view visible to start showing the preview. This triggers the
    // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
    previewDisplayView.setVisibility(View.VISIBLE);
  }

  protected Size cameraTargetResolution() {
    return null; // No preference and let the camera (helper) decide.
  }

  public void startCamera() {
    cameraHelper = new CameraXPreviewHelper();
    previewFrameTexture = converter.getSurfaceTexture();
    cameraHelper.setOnCameraStartedListener(
        surfaceTexture -> {
          Log.e("surfaceTexture",""+surfaceTexture);
          onCameraStarted(surfaceTexture);
        });
    CameraHelper.CameraFacing cameraFacing =
        applicationInfo.metaData.getBoolean("cameraFacingFront", false)
            ? CameraHelper.CameraFacing.FRONT
            : CameraHelper.CameraFacing.BACK;
    cameraHelper.startCamera(
        this, new ImageCapture.Builder(), cameraFacing, previewFrameTexture, cameraTargetResolution());

    actionButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Log.e("susu", "SUK");
        File file = new File(Environment.getExternalStorageDirectory().toString() + "/Pictures", System.currentTimeMillis()+"contoh.jpeg");

//        Bitmap bitmap = getBitmapFromView(frameLayout);
        try {
          FileOutputStream out = new FileOutputStream(file);
          bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
          out.flush();
          out.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
//        cameraHelper.takePicture(file, new ImageCapture.OnImageSavedCallback() {
//          @Override
//          public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
////            Toast.makeText(getApplicationContext(), "Success", Toast.LENGTH_SHORT).show();
//            Log.e(TAG, "berhasil");
//          }
//
//          @Override
//          public void onError(@NonNull ImageCaptureException exception) {
//            Log.e(TAG, String.format(
//                    "error : %s", exception.toString()));
//          }
//        });
      }
    });

  }
  public Bitmap getBitmapFromView(View view) {
    view.setDrawingCacheEnabled(true);
    Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
    return bitmap;
  }

  public Bitmap viewToBitmap(View view) {
    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    view.draw(canvas);
    return bitmap;
  }
  protected Size computeViewSize(int width, int height) {
    return new Size(width, height);
  }

  protected void onPreviewDisplaySurfaceChanged(
      SurfaceHolder holder, int format, int width, int height) {
    // (Re-)Compute the ideal size of the camera-preview display (the area that the
    // camera-preview frames get rendered onto, potentially with scaling and rotation)
    // based on the size of the SurfaceView that contains the display.
    Size viewSize = computeViewSize(width, height);
    Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
    boolean isCameraRotated = cameraHelper.isCameraRotated();

    // Configure the output width and height as the computed display size.
    converter.setDestinationSize(
        isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
        isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
  }

  private void setupPreviewDisplayView() {
    previewDisplayView.setVisibility(View.GONE);
    actionButton = findViewById(R.id.floatingActionButton);
    viewGroup = findViewById(R.id.preview_display_layout);
    viewGroup.addView(previewDisplayView);

    previewDisplayView
        .getHolder()
        .addCallback(
            new SurfaceHolder.Callback() {
              @Override
              public void surfaceCreated(SurfaceHolder holder) {
                Log.e("holder",""+holder);
                Log.e("holder2",""+holder.getSurface());
                Log.e("holder2.5",""+processor.getVideoSurfaceOutput());
                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
              }

              @Override
              public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.e("holder3",""+format);
                onPreviewDisplaySurfaceChanged(holder, format, width, height);
              }

              @Override
              public void surfaceDestroyed(SurfaceHolder holder) {
                processor.getVideoSurfaceOutput().setSurface(null);
              }
            });
  }

  private static String getMultiFaceLandmarksDebugString(
          List<ClassificationProto.ClassificationList> multiFaceLandmarks) {
    if (multiFaceLandmarks.isEmpty()) {
      return "No face landmarks";
    }
    String multiFaceLandmarksStr = "Number of faces detected: " + multiFaceLandmarks.size() + "\n";
    int faceIndex = 0;
    for (ClassificationProto.ClassificationList landmarks : multiFaceLandmarks) {
      multiFaceLandmarksStr +=
              "\t#Face landmarks for face[" + faceIndex + "]: " + landmarks.getClassificationCount() + "\n";
      int landmarkIndex = 0;
      for (ClassificationProto.Classification landmark : landmarks.getClassificationList()) {
        multiFaceLandmarksStr +=
                "\t\tLandmark ["
                        + landmarkIndex
                        + "]: ("
                        + landmark.getDisplayName()
                        + ", "
                        + landmark.getIndex()
                        + ", "
                        + landmark.getScore()
                        + ")\n";
        ++landmarkIndex;
      }
      ++faceIndex;
    }
    return multiFaceLandmarksStr;
  }
}
