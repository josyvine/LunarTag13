package com.safevoice.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.safevoice.app.databinding.ActivityKycBinding;
import com.safevoice.app.utils.FaceVerifier;
import com.safevoice.app.utils.ImageUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class KycActivity extends AppCompatActivity {

    private static final String TAG = "KycActivity";
    private static final double FACE_MATCH_THRESHOLD = 0.8;

    private enum KycState {
        SCANNING_ID,
        SCANNING_FACE,
        VERIFYING,
        COMPLETE
    }

    private ActivityKycBinding binding;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService analysisExecutor;
    private FaceVerifier faceVerifier;
    private ProcessCameraProvider cameraProvider;

    private volatile KycState currentState = KycState.SCANNING_ID;
    private float[] idCardEmbedding = null;
    private String verifiedName = null;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityKycBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        analysisExecutor = Executors.newSingleThreadExecutor();

        try {
            faceVerifier = new FaceVerifier(this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load FaceVerifier model.", e);
            Toast.makeText(this, "Error: Verification model could not be loaded.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        startCamera();
        updateUIForState();
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                this.cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is not available to bind use cases.");
            return;
        }

        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = (currentState == KycState.SCANNING_ID) ?
                CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(analysisExecutor, new KycImageAnalyzer());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            Log.i(TAG, "Successfully bound camera for state: " + currentState);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }

        preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
    }

    private void proceedToFaceScan() {
        Log.d(TAG, "Proceeding to face scan.");
        currentState = KycState.SCANNING_FACE;
        updateUIForState();
    }

    private void updateUIForState() {
        runOnUiThread(() -> {
            switch (currentState) {
                case SCANNING_ID:
                    binding.guideBox.setVisibility(View.VISIBLE);
                    binding.guideBox.setBackgroundResource(R.drawable.bg_guide_box);
                    binding.textInstructions.setText(R.string.kyc_instructions_id);
                    break;
                case SCANNING_FACE:
                    binding.guideBox.setVisibility(View.VISIBLE);
                    binding.guideBox.setBackgroundResource(R.drawable.oval_frame_drawable);
                    binding.textInstructions.setText(R.string.kyc_instructions_face);
                    bindCameraUseCases();
                    break;
                case VERIFYING:
                    binding.guideBox.setVisibility(View.VISIBLE);
                    binding.textInstructions.setText(R.string.kyc_status_verifying);
                    binding.progressBar.setVisibility(View.VISIBLE);
                    break;
                case COMPLETE:
                    binding.guideBox.setVisibility(View.GONE);
                    binding.progressBar.setVisibility(View.GONE);
                    break;
            }
        });
    }

    private class KycImageAnalyzer implements ImageAnalysis.Analyzer {
        private final TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        private final FaceDetector faceDetector;

        KycImageAnalyzer() {
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build();
            faceDetector = FaceDetection.getClient(options);
        }

        @Override
        @SuppressLint("UnsafeOptInUsageError")
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (currentState != KycState.SCANNING_ID && currentState != KycState.SCANNING_FACE) {
                imageProxy.close();
                return;
            }

            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            if (currentState == KycState.SCANNING_ID) {
                processIdCardImage(image, imageProxy).addOnCompleteListener(task -> imageProxy.close());
            } else if (currentState == KycState.SCANNING_FACE) {
                processLiveFaceImage(image, imageProxy).addOnCompleteListener(task -> imageProxy.close());
            } else {
                imageProxy.close();
            }
        }

        private Task<Void> processIdCardImage(InputImage image, ImageProxy imageProxy) {
            Task<Text> textRecognitionTask = (verifiedName == null) ? textRecognizer.process(image) : Tasks.forResult(null);
            Task<List<Face>> faceDetectionTask = (idCardEmbedding == null) ? faceDetector.process(image) : Tasks.forResult(null);

            return Tasks.whenAll(textRecognitionTask, faceDetectionTask)
                .addOnSuccessListener(aVoid -> {
                    if (verifiedName == null) {
                        Text visionText = textRecognitionTask.getResult();
                        if (visionText != null) {
                            String name = extractNameFromText(visionText);
                            if (name != null) verifiedName = name;
                        }
                    }

                    if (idCardEmbedding == null) {
                        List<Face> faces = faceDetectionTask.getResult();
                        if (faces != null && !faces.isEmpty()) {
                            Bitmap croppedFace = ImageUtils.cropAndConvert(imageProxy, faces.get(0).getBoundingBox());
                            if (croppedFace != null) idCardEmbedding = faceVerifier.getFaceEmbedding(croppedFace);
                        }
                    }

                    if (verifiedName != null && idCardEmbedding != null) {
                        if (isProcessing.compareAndSet(false, true)) {
                            runOnUiThread(() -> proceedToFaceScan());
                        }
                    }
                });
        }

        private Task<List<Face>> processLiveFaceImage(InputImage image, ImageProxy imageProxy) {
            return faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty() && isProcessing.compareAndSet(true, false)) {
                        currentState = KycState.VERIFYING;
                        updateUIForState();

                        Bitmap croppedFace = ImageUtils.cropAndConvert(imageProxy, faces.get(0).getBoundingBox());
                        if (croppedFace != null) {
                            float[] liveEmbedding = faceVerifier.getFaceEmbedding(croppedFace);
                            double similarity = faceVerifier.calculateSimilarity(idCardEmbedding, liveEmbedding);
                            Log.i(TAG, "Face similarity score: " + similarity);

                            if (similarity > FACE_MATCH_THRESHOLD) {
                                handleVerificationSuccess();
                            } else {
                                handleVerificationFailure("Face does not match ID.");
                            }
                        }
                    }
                });
        }
    }

    private String extractNameFromText(Text visionText) {
        if (visionText == null) return null;
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();
                if (lineText.matches("([A-Z][a-zA-Z]*[.]?[ ]?){2,3}")) {
                    if (!lineText.matches(".*[0-9].*") && lineText.length() < 30) {
                        return lineText;
                    }
                }
            }
        }
        return null;
    }

    // --- THIS IS THE CORRECTED METHOD ---
    private void handleVerificationSuccess() {
        Log.i(TAG, "Verification SUCCESSFUL. Name: " + verifiedName);
        currentState = KycState.COMPLETE;
        updateUIForState();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("isVerified", true);
            userData.put("verifiedName", verifiedName);
            // --- FIX: ADD THE USER'S EMAIL TO THE DATA BEING SAVED ---
            // This ensures the email is in the database and can be searched.
            userData.put("email", user.getEmail());

            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .set(userData, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(KycActivity.this, "Verification successful!", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                         Toast.makeText(KycActivity.this, "Verification successful, but failed to save.", Toast.LENGTH_LONG).show();
                         finish();
                     });
        } else {
             Toast.makeText(this, "Verification successful, but no signed-in user found.", Toast.LENGTH_LONG).show();
             finish();
        }
    }

    private void handleVerificationFailure(String reason) {
        Log.e(TAG, "Verification FAILED. Reason: " + reason);
        currentState = KycState.COMPLETE;
        updateUIForState();
        Toast.makeText(this, "Verification Failed: " + reason, Toast.LENGTH_LONG).show();
        new android.os.Handler(Looper.getMainLooper()).postDelayed(this::finish, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
    }
}