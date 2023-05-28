package com.example.tfg_dmae;

// Copyright 2021 The MediaPipe Authors.
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

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Range;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Main activity of MediaPipe Hands app. */
public class MediaPipeActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {
    private static final String TAG = "MainActivity";

    //-------------------- CONSTANTS
    boolean DEFAULT_NARRATOR = true;

    private final float DEFAULT_ZOOM = 1.0f;
    private final float INCREASE_ZOOM_FACTOR = 1.1f;
    private final float DECREASE_ZOOM_FACTOR = 0.9f;

    private final int DEFAULT_EXPOSURE = 0;
    private final int INCREASE_EXPOSURE_FACTOR = 10;
    private final int DECREASE_EXPOSURE_FACTOR = -10;

    private final int DEFAULT_BRIGHTNESS = 100;
    private final int INCREASE_BRIGHTNESS_FACTOR = 17;
    private final int DECREASE_BRIGHTNESS_FACTOR = -17;

    private final long MINIMUM_EXECUTION_INTERVAL = 2000;

    private final int MIN_BRIGHTNESS = 0;
    private final int MAX_BRIGHTNESS = 255;

    private final String APP_PREFERENCES_ROUTE = "DMAE_APP_PREFERENCES";

    //-------------------- CONFIGURATION
    boolean narrator;

    //-------------------- MediaPipe
    private Hands hands;
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;

    //-------------------- Python
    private Python py;
    private PyObject pyObject;

    //-------------------- CameraX
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    Camera previewCamera;
    PreviewView previewView;
    Preview preview;
    CameraControl cameraControl;
    CameraInfo cameraInfo;
    Window window;
    Integer height;
    Integer width;

    //-------------------- Zoom/Exposure/Brightness variables
    Float currentZoom;
    Integer currentExposure;
    Integer currentBrightness;
    float maxZoomRatio;
    float minZoomRatio;
    Range<Integer> exposureRange;
    //-------------------- Gesture Processing
    private TextView gestureLabel;
    private int lastActionGesture = -1;

    //-------------------- Gesture recognition timeOut
    private long lastExecutionTime = 0;
    long currentTime;

    //-------------------- Settings permissions
    ContentResolver contentResolver;
    boolean settingsCanWrite;
    Context context;

    //-------------------- Narrator
    private TextToSpeech textToSpeech;

    //-------------------- ViewComponents
    ImageButton userManualButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_pipe);

        window = getWindow();

        //Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Prevent screenshots or screen recording
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        checkCameraPermission();

        contentResolver = getContentResolver();
        context = getApplicationContext();

        settingsCanWrite = Settings.System.canWrite(context);

        if(!settingsCanWrite)
            showPermissionsDialog();

        //cameraX initialization
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
                loadConfiguration();
                createNarrator();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }, getExecutor());

        //Image processing by frames (MediaPipe
        setupStaticImageModePipeline();

        //Gets python instance
        py = Python.getInstance();
        pyObject = py.getModule("app");

        //Components
        previewView = findViewById(R.id.previewView);
        gestureLabel = findViewById(R.id.gestureLabel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastActionGesture = -1;
        ViewTreeObserver observer = previewView.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove the listener to avoid continuously getting callbacks
                previewView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // Get the width and height of previewView
                width = previewView.getWidth();
                height = previewView.getHeight();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    void loadConfiguration(){
        SharedPreferences sharedPref = getSharedPreferences(APP_PREFERENCES_ROUTE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        currentZoom = sharedPref.getFloat("zoom", DEFAULT_ZOOM);
        currentExposure = sharedPref.getInt("exposure", DEFAULT_EXPOSURE);
        currentBrightness = sharedPref.getInt("brightness", DEFAULT_BRIGHTNESS);

        //Carga el narrador por defecto ya que actualmente no hay forma de editarlo
        //En caso de querer tener la opción de desactivarlo añadir un setBoolean de esta característica y la siguiente línea se encargará de cargarlo
        narrator = sharedPref.getBoolean("narrator", DEFAULT_NARRATOR);

        setZoom();
        setExposure();
        setBrightness();
    }

    private boolean checkLanguageSupport() {
        int result = textToSpeech.isLanguageAvailable(new Locale("es"));
        if (result == TextToSpeech.LANG_AVAILABLE) {
            return true;
        } else {
            return false;
        }
    }

    private void checkCameraPermission(){
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    private void checkWriteSettingsPermission(){
        //Ask premission
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        startActivity(intent);
    }

    private void showLanguageNarratorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Necesita el Narrador");
        builder.setMessage("Para utilizar esta aplicación necesita tener instalado el Narrador.");
        builder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();

            }
        });
        builder.setNegativeButton("Cerrar aplicación", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showPermissionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permiso a los ajustes");
        builder.setMessage(
                "Para controlar el brillo del dispositivo la aplicación requiere permiso para modificar los ajustes.\n" +
                        "\n" +
                        "Por favor, asegurese de conceder dicho permiso para que la aplicación funcionar correctamente.\n" +
                        "\n" +
                        "Al cerrar este diálogo, se abrirá una nueva pestaña, debe buscar la aplicación TFG_DMAE, hacer click sobre ella y concenderle el permiso."
        );
        builder.setCancelable(false);
        builder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                checkWriteSettingsPermission();
                dialogInterface.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    //-------------------- CAMERAX

    @NonNull
    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private void startCameraX(@NonNull ProcessCameraProvider cameraProvider) {

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview = new Preview.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        //Analysis mode for processing frame by frame
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), this);

        //bind to lifecycle:
        previewCamera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);

        //Get cameraControl
        cameraControl = previewCamera.getCameraControl();
        //Get cameraInfo
        cameraInfo = previewCamera.getCameraInfo();

        //Get camera parameters
        maxZoomRatio = cameraInfo.getZoomState().getValue().getMaxZoomRatio();
        minZoomRatio = cameraInfo.getZoomState().getValue().getMinZoomRatio();
        exposureRange = cameraInfo.getExposureState().getExposureCompensationRange();
    }

    private void createNarrator(){
        //Narrator
        if (narrator) {
            textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        if (!checkLanguageSupport()) {
                            showLanguageNarratorDialog();
                        }
                        int result = textToSpeech.setLanguage(new Locale("es", "ES"));
                        //int result = textToSpeech.setLanguage(Locale.ENGLISH);
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("TTS", "Este lenguaje no está soportado");
                        }
                    } else {
                        Log.e("TTS", "Inicialización del TextToSpeech fallida");
                    }
                }
            });
        }
    }

    //CameraX listener for frame processing
    @Override
    public void analyze(@NonNull ImageProxy image) {
        Bitmap bitmap = previewView.getBitmap();
        image.close();

        if (bitmap != null)
            //Tries to recognize a hand
            hands.send(bitmap);
    }

    //-------------------- GESTURE MANAGEMENT

    //Recognizes landmarks associated gesture using a python script
    private int gestureRecognition(List<NormalizedLandmark> landmarks){
        //Creates a json with landmark info (compatible format with python)
        Gson gson = new Gson();
        String json = gson.toJson(landmarks);
        //Calls python script for gesture recognition
        PyObject gestureId = pyObject.callAttr("main", json, width, height);

        return gestureId.toInt();
    }

    private void gestureProcessing(int gestureId){
        currentTime = System.currentTimeMillis();
        //Makes the associated gesture action if interval has elapsed
        if (currentTime - lastExecutionTime >= MINIMUM_EXECUTION_INTERVAL) {
            String gesture = getGestureNameById(gestureId);
            //Changes gestureLabel with the actual gesture
            gestureLabel.setText(gesture);
            if (narrator) {
                textToSpeech.speak(gesture, TextToSpeech.QUEUE_ADD, null, "GESTURE");
            }
            //Runs on main thread to avoid crashing
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //makes associated gesture action
                    gestureToAction(gestureId);
                }
            });
            lastExecutionTime = currentTime;
        }
    }

    private String getGestureNameById(Integer id) {
        switch (id) {
            case 0:
                return "ZOOM";
            case 1:
                return "BRILLO";
            case 2:
                return "CONTRASTE";
            case 3:
                return "AUMENTAR";
            case 4:
                return "DISMINUIR";
            case 5:
                return "RESETEAR";
            default:
                return "Gesto no detectado";
        }
    }

    //Cases 0,1 and 2 detects which gesture has been used,
    //3 and 4 increase or decrease selected option
    private void gestureToAction(Integer id) {
        switch (id) {
            case 0:
            case 1:
            case 2:
                lastActionGesture = id;
                break;
            case 3:
                manageIncrease();
                break;
            case 4:
                manageDecrease();
                break;
            case 5:
                resetValues();
            default:
                break;
        }
    }

    //-------------------- INCREASE/DECREASE MANAGEMENT

    private void setZoom(){
        //Sets new zoom value
        cameraControl.setZoomRatio(currentZoom);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        SharedPreferences sharedPref = getSharedPreferences(APP_PREFERENCES_ROUTE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat("zoom", currentZoom);
        editor.apply();
    }

    private void zoom(float factor){
        //Obtains current zoom
        currentZoom = cameraInfo.getZoomState().getValue().getZoomRatio() * factor;
        System.out.println("ZOOM: " + currentZoom);
        if (currentZoom > maxZoomRatio) {
            if (narrator)
                textToSpeech.speak("No se puede aumentar mas el zoom", TextToSpeech.QUEUE_ADD, null, "INCREASE-TOAST");
            Toast.makeText(context, "No se puede aumentar más el zoom", Toast.LENGTH_SHORT).show();
        }
        else if (currentZoom < minZoomRatio){
            if (narrator)
                textToSpeech.speak("No se puede disminuir mas el zoom", TextToSpeech.QUEUE_ADD, null, "DECREASE-TOAST");
            Toast.makeText(context, "No se puede disminuir más el zoom", Toast.LENGTH_SHORT).show();
        }
        else {
            setZoom();
        }
    }

    private void setBrightness(){
        if(settingsCanWrite){
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, currentBrightness);
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.screenBrightness = currentBrightness;
            window.setAttributes(layoutParams);
            SharedPreferences sharedPref = getSharedPreferences(APP_PREFERENCES_ROUTE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt("brightness", currentBrightness);
            editor.apply();
        }
    }

    private void brightness(int factor){
        try {
            settingsCanWrite = Settings.System.canWrite(context);
            if(settingsCanWrite) {
                currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)+ factor;
                if (currentBrightness < MIN_BRIGHTNESS) {
                    if (narrator)
                        textToSpeech.speak("No se puede disminuir mas el brillo", TextToSpeech.QUEUE_ADD, null, "DECREASE-TOAST");
                    Toast.makeText(context, "No se puede disminuir más el brillo", Toast.LENGTH_SHORT).show();
                }
                else if (currentBrightness > MAX_BRIGHTNESS){
                    if (narrator)
                        textToSpeech.speak("No se puede aumentar mas el brillo", TextToSpeech.QUEUE_ADD, null, "INCREASE-TOAST");
                    Toast.makeText(context, "No se puede aumentar más el brillo", Toast.LENGTH_SHORT).show();
                }
                else {
                    setBrightness();
                }
            }
            else {
                Toast.makeText(context, "No se puede modificar el brillo", Toast.LENGTH_SHORT).show();
                textToSpeech.speak("No se puede modificar el brillo", TextToSpeech.QUEUE_ADD, null, "NO-NARRATOR-TOAST");
            }
        } catch (Settings.SettingNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void setExposure(){
        cameraControl.setExposureCompensationIndex(currentExposure);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        SharedPreferences sharedPref = getSharedPreferences(APP_PREFERENCES_ROUTE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("exposure", currentExposure);
        editor.apply();
    }

    private void exposure(int factor){
        CameraControl cameraControl = previewCamera.getCameraControl();
        ExposureState exposureState = previewCamera.getCameraInfo().getExposureState();
        if (exposureState.isExposureCompensationSupported()){
            currentExposure = exposureState.getExposureCompensationIndex() + factor;
            if (exposureRange.contains(currentExposure)){
                setExposure();
            }
            else if (factor > 0) {
                if (narrator)
                    textToSpeech.speak("No se puede aumentar mas el contraste", TextToSpeech.QUEUE_ADD, null, "INCREASE-TOAST");
                Toast.makeText(context, "No se puede aumentar más el contraste", Toast.LENGTH_SHORT).show();
            }
            else{
                if (narrator)
                    textToSpeech.speak("No se puede disminuir mas el contraste", TextToSpeech.QUEUE_ADD, null, "DECREASE-TOAST");
                Toast.makeText(context, "No se puede disminuir más el contraste", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void manageIncrease() {
        if (lastActionGesture == 0)
            zoom(INCREASE_ZOOM_FACTOR);
        else if (lastActionGesture == 1)
            brightness(INCREASE_BRIGHTNESS_FACTOR);
        else if (lastActionGesture == 2)
            exposure(INCREASE_EXPOSURE_FACTOR);
        else {
            if (narrator)
                textToSpeech.speak("No se ha seleccionado una característica a modificar", TextToSpeech.QUEUE_ADD, null, "INCREASE-TOAST");
            Toast.makeText(context, "No se ha seleccionado una característica a modificar", Toast.LENGTH_SHORT).show();
        }
    }

    private void manageDecrease(){
        if (lastActionGesture == 0)
            zoom(DECREASE_ZOOM_FACTOR);
        else if (lastActionGesture == 1)
            brightness(DECREASE_BRIGHTNESS_FACTOR);
        else if (lastActionGesture == 2)
            exposure(DECREASE_EXPOSURE_FACTOR);
        else {
            if (narrator)
                textToSpeech.speak("No se ha seleccionado una característica a modificar", TextToSpeech.QUEUE_ADD, null, "DECREASE-TOAST");
            Toast.makeText(context, "No se ha seleccionado una característica a modificar", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetValues(){
        currentZoom = DEFAULT_ZOOM;
        setZoom();
        currentExposure = DEFAULT_EXPOSURE;
        setExposure();
        currentBrightness = DEFAULT_BRIGHTNESS;
        setBrightness();
        lastActionGesture = -1;
    }
    //-------------------- MEDIAPIPE PROCESSING

    //MediaPipe hand recognition for static images (frames)s
    private void setupStaticImageModePipeline() {
        // Initializes a new MediaPipe Hands solution instance in the static image mode.
        hands =
                new Hands(this, HandsOptions.builder()
                        .setStaticImageMode(true)
                        .setMaxNumHands(2)
                        .setRunOnGpu(RUN_ON_GPU)
                        .build());
        // Listener to detect hand
        hands.setResultListener(
                handsResult -> {
                    logWristLandmark(handsResult);
                    if (handsResult.multiHandLandmarks().size() > 0 || handsResult.multiHandWorldLandmarks().size() > 0) {
                        //Gets a normalized landmark list from the first hand
                        List<NormalizedLandmark> landmarks = handsResult.multiHandLandmarks().get(0).getLandmarkList();
                        gestureProcessing(gestureRecognition(landmarks));
                    }
                });
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));
    }

    //Gets normalized landmarks points for given hand
    private void logWristLandmark(HandsResult result) {
        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }
        NormalizedLandmark wristLandmark = result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return;
        }
        Landmark wristWorldLandmark =
                result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
    }

    //-------------------- APP NAVIGATION
    public void myImageButtonClick(View view) {
        Intent intent = new Intent(this, UserManual.class);
        startActivity(intent);
    }
}
