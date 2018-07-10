package com.zeek.zeekpicnotes;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity  implements AdapterView.OnItemSelectedListener {

    private Button btnCapture;
    private TextureView textureView;
    private Button btnAbout;

    private static final SparseIntArray ORIENTATIONS  = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);

    }

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDim;
    private ImageReader imageReader;

    //FILE
    private File file;
    private static String selectedFolder = "Default";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBgHandler;
    private HandlerThread mBgThread;

    CameraDevice.StateCallback stateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.textureView1);
        textureView.setSurfaceTextureListener(textureListener);
        btnCapture = (Button)findViewById(R.id.buttonCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);
        List<String> availableFolders = new ArrayList<String>();
        availableFolders.add("Default");
        availableFolders.add("Work");
        availableFolders.add("Personal");
        availableFolders.add("Task");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, availableFolders);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(dataAdapter);

        btnAbout = (Button)findViewById(R.id.buttonAbout);
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder1  = new AlertDialog.Builder(MainActivity.this);
                builder1.setMessage("ZeeK Pic Notes:\n===============\n* Capture notes and save it to a specific album within your gallery.\n* Don't forget to select album before capturing.\n\nThanks\n-Aman Verma\navd.zeek@gmail.com");
                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert11 = builder1.create();
                alert11.show();
            }
        });



    }

    private void takePicture() {
        if (cameraDevice == null)
            return;
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSize = null;
            if (characteristics != null){
                jpegSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }

            //defining size, defaulting values
            int width = 1024;
            int height = 768;
           /* if (jpegSize!=null && jpegSize.length>0){
                width = jpegSize[1].getWidth();
                height = jpegSize[1].getHeight();
            }*/
           // System.out.println("width = "+width+" and height = "+height);
            ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>();
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //check orientation base on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+"/ZeeKPicNotes/"+selectedFolder);
            if (!folder.exists()){
                folder.mkdirs();
            }
            file = new File(folder.getAbsolutePath()+"/"+UUID.randomUUID().toString()+".jpg");


            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener(){

                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try{
                        image = reader.acquireLatestImage();
                        ByteBuffer  buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    }catch(Exception e){
                        e.printStackTrace();
                    }finally{
                        if (image != null)
                            image.close();
                    }
                }

                private void save(byte[] bytes) throws IOException{
                    OutputStream outputstream = null;
                    try {
                        outputstream = new FileOutputStream(file);
                        outputstream.write(bytes);

                    }catch(Exception e){
                        e.printStackTrace();
                    }finally{
                        if (outputstream != null)
                            outputstream.close();
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, mBgHandler);
            final CameraCaptureSession.CaptureCallback capturelistener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved: "+file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }

            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), capturelistener, mBgHandler);

                    }catch(CameraAccessException e){
                        e.printStackTrace();;
                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },mBgHandler);



        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDim.getWidth(), imageDim.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();


                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this,"Changed",Toast.LENGTH_SHORT).show();

                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null){
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBgHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private   void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map!= null;
            imageDim = map.getOutputSizes(SurfaceTexture.class)[0];
            System.out.println("CAMERA OPEN ******* width = "+imageDim.getHeight()+" **** height = "+imageDim.getHeight());
            //for API > 23, check realtime permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallBack, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION){
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"No Permission for Camera!!!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }


    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()){
                openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        mBgThread.quitSafely();
        try{
            mBgThread.join();
            mBgThread = null;
            mBgHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBgThread = new HandlerThread("Camera bg thread");
        mBgThread.start();
        mBgHandler = new Handler(mBgThread.getLooper());

    }


    //SPINNER ADAPTER METHODS...
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String item = parent.getItemAtPosition(position).toString();
        selectedFolder=item;
        Toast.makeText(parent.getContext(), "Selected: " + item, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
