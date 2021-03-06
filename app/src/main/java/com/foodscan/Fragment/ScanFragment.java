package com.foodscan.Fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.foodscan.Activity.MainActivity;
import com.foodscan.Activity.ProductDetailsActivity;
import com.foodscan.Interfaces.FlashLightChangeListner;
import com.foodscan.OCR.CameraSource;
import com.foodscan.OCR.CameraSourcePreview;
import com.foodscan.OCR.GraphicOverlay;
import com.foodscan.OCR.OcrDetectorProcessor;
import com.foodscan.OCR.OcrGraphic;
import com.foodscan.R;
import com.foodscan.Utility.TinyDB;
import com.foodscan.Utility.UserDefaults;
import com.foodscan.Utility.Utility;
import com.foodscan.WsHelper.helper.Attribute;
import com.foodscan.WsHelper.helper.WebserviceWrapper;
import com.foodscan.WsHelper.model.DTOProductDetailsData;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.rey.material.app.ThemeManager;

import java.io.IOException;
import java.text.Normalizer;

import io.realm.Realm;

public class ScanFragment extends Fragment implements WebserviceWrapper.WebserviceResponse, FlashLightChangeListner {

    // Constants used to pass extra data in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";
    public static final String TextBlockObject = "String";
    private static final String TAG = ScanFragment.class.getSimpleName();
    // Intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;
    // Permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private Context mContext;
    private Realm realm;
    //public DTOUser dtoUser;
    private TinyDB tinyDB;
    private View viewFragment;
    private RelativeLayout ll_parent;
    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview, preview_barcode;
    private GraphicOverlay<OcrGraphic> mGraphicOverlay;

    // Helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private String productName = "";
    //private boolean isOnCreateCalled = false;

    private TextView txt_product, txt_barcode;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("!_@", "activity called");
        manageCamera();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    /**
     * Initializes the UI and creates the detector pipeline.
     */
//    @Override
//    public void setUserVisibleHint(boolean isVisibleToUser) {
//        super.setUserVisibleHint(isVisibleToUser);
//        if (isVisibleToUser) {
//            Log.e("!_@_", "---visible--");
//            isOnCreateCalled = true;
//            onResume();
//        } else {
//            Log.e("!_@_", "---not visible--");
//        }
//    }


    // Intent request code to handle updating play services if needed.
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        viewFragment = inflater.inflate(R.layout.scan_fragment, container, false);
        mContext = getActivity();
        //  isOnCreateCalled = true;
        mContext = getActivity();
        realm = Realm.getDefaultInstance();
        tinyDB = new TinyDB(mContext);
        initView(viewFragment);
        initGlobals();

        return viewFragment;
    }


    private void initView(View viewFragment) {

        ll_parent = viewFragment.findViewById(R.id.ll_parent);
        mPreview = (CameraSourcePreview) viewFragment.findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay<OcrGraphic>) viewFragment.findViewById(R.id.graphicOverlay);

        gestureDetector = new GestureDetector(mContext, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(mContext, new ScaleListener());

//        graphicOverlay_barcode = (GraphicOverlay<BarcodeGraphic>) viewFragment.findViewById(R.id.graphicOverlay_barcode);

        txt_product = viewFragment.findViewById(R.id.txt_product);
        txt_barcode = viewFragment.findViewById(R.id.txt_barcode);

        preview_barcode = viewFragment.findViewById(R.id.preview_barcode);

    }

    private void initGlobals() {

        txt_product.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mPreview.setVisibility(View.VISIBLE);
                preview_barcode.setVisibility(View.GONE);
            }
        });

        txt_barcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mPreview.setVisibility(View.GONE);
                preview_barcode.setVisibility(View.VISIBLE);

            }
        });

        viewFragment.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {

                boolean b = scaleGestureDetector.onTouchEvent(e);
                boolean c = gestureDetector.onTouchEvent(e);
                return b || c;

            }
        });
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the ocr detector to detect small text samples
     * at long distances.
     * <p>
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        Context context = mContext;

        // A text recognizer is created to find text.  An associated processor instance
        // is set to receive the text recognition results and display graphics for each text block
        // on screen.
        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();
        textRecognizer.setProcessor(new OcrDetectorProcessor(mGraphicOverlay));

//        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
//        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(graphicOverlay_barcode, mContext);
//        barcodeDetector.setProcessor(
//                new MultiProcessor.Builder<>(barcodeFactory).build());
////
//        MultiDetector multiDetector = new MultiDetector.Builder()
//                .add(textRecognizer)
//                .add(barcodeDetector)
//                .build();


        if (!textRecognizer.isOperational())
        //if (!multiDetector.isOperational())
        {
            // Note: The first time that an app using a Vision API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any text,
            // barcodes, or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = mContext.registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                //low storage
                Toast.makeText(mContext, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));

            }
        }


        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the text recognizer to detect small pieces of text.
        mCameraSource =
                new CameraSource.Builder(getActivity().getApplicationContext(), textRecognizer)
                        .setFacing(CameraSource.CAMERA_FACING_BACK)
                        .setRequestedPreviewSize(1280, 1024)
                        .setRequestedFps(2.0f)
                        .setFlashMode(UserDefaults.isFLashLightOn ? Camera.Parameters.FLASH_MODE_TORCH : null)
                        .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
                        .build();
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission(boolean showSettingScreen) {
        if (showSettingScreen) {
            showErrorDialog(true);
        } else {
            final String[] permissions = new String[]{Manifest.permission.CAMERA};
            requestPermissions(permissions, RC_HANDLE_CAMERA_PERM);
        }
    }

    /**
     * onTap is called to capture the first TextBlock under the tap location and return it to
     * the Initializing Activity.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    private boolean onTap(float rawX, float rawY) {
        OcrGraphic graphic = mGraphicOverlay.getGraphicAtLocation(rawX, rawY);
        TextBlock text = null;
        if (graphic != null) {
            text = graphic.getTextBlock();
            if (text != null && text.getValue() != null) {

                //Toast.makeText(mContext, "selected text :" + text.getValue(), Toast.LENGTH_SHORT).show();
                showTextDialog(text.getValue());

            } else {
                Log.d(TAG, "text data is null");
            }
        } else {
            Log.d(TAG, "no text detected");
        }

        return text != null;
    }

    private void showTextDialog(String detectedText) {

        detectedText = detectedText.replaceAll("[\\t\\n\\r]+", " ");

        final Dialog d = new Dialog(mContext);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        d.setContentView(R.layout.dialog_detected_text);
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(true);

        boolean isLightTheme = ThemeManager.getInstance().getCurrentTheme() == 0;

        d.getWindow().getAttributes().windowAnimations = (isLightTheme ? R.style.SimpleDialogLight : R.style.SimpleDialog);//R.style.DialogAnimation

        final EditText edt_detected_text = d.findViewById(R.id.edt_detected_text);
        edt_detected_text.setText(detectedText);

        edt_detected_text.setSelection(edt_detected_text.getText().length());

        edt_detected_text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edt_detected_text.setCursorVisible(true);
            }
        });

        TextView txt_viewproduct = d.findViewById(R.id.txt_viewproduct);
        txt_viewproduct.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (Utility.validateStringPresence(edt_detected_text)) {

                    productName = edt_detected_text.getText().toString();
                    //  LifyzerFragment.productName = edt_detected_text.getText().toString();

                    productName = Normalizer.normalize(productName, Normalizer.Form.NFD);
                    productName = productName.replaceAll("[^\\p{ASCII}]", "");
                    //   LifyzerFragment.productName = LifyzerFragment.productName.replaceAll("[^\\p{ASCII}]", "");

                    if (((MainActivity) mContext).dtoUser != null) {
                        wsCallProductDetails();
                    } else {

                        //Toast.makeText(mContext, "Please login", Toast.LENGTH_SHORT).show();
                        ((MainActivity) mContext).showLoginDialog();
                    }

                }

                d.dismiss();
            }
        });

        d.show();
    }

    private void displayNoResultDialog() {

        final Dialog d = new Dialog(mContext);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        d.setContentView(R.layout.dialog_no_result_found);
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(true);

        boolean isLightTheme = ThemeManager.getInstance().getCurrentTheme() == 0;

        d.getWindow().getAttributes().windowAnimations = (isLightTheme ? R.style.SimpleDialogLight : R.style.SimpleDialog);//R.style.DialogAnimation

        TextView txt_ok = d.findViewById(R.id.txt_ok);
        txt_ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        d.show();
    }

    /**
     * Restarts the camera.
     */
    @Override
    public void onResume() {
        super.onResume();
        Log.e("!_@_", "Onresume call");
        try {
            //if (isOnCreateCalled)
            {
                manageCamera();
                //    isOnCreateCalled = false;
            }
            startCameraSource();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void manageCamera() {
        // read parameters from the intent used to launch the activity.
        boolean autoFocus = getActivity().getIntent().getBooleanExtra(AutoFocus, true);
        boolean useFlash = getActivity().getIntent().getBooleanExtra(UseFlash, false);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.


        int rc = ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash);
        } else {
            if (Utility.getPermissionStatus(getActivity(), Manifest.permission.CAMERA) == Utility.DENIED) {
                Log.e("!_@_", "----<<<< DENIED >>>>>-------------");
                requestCameraPermission(true);
            } else {
                requestCameraPermission(false);
            }
        }
    }

    /**
     * Stops the camera.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // We have permission, so create the camerasource
            boolean autoFocus = getActivity().getIntent().getBooleanExtra(AutoFocus, true);
            boolean useFlash = getActivity().getIntent().getBooleanExtra(UseFlash, false);
            createCameraSource(autoFocus, useFlash);
            return;
        }
        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));


        showErrorDialog(false);

    }

    private void showErrorDialog(final boolean showSettingScreen) {
        DialogInterface.OnClickListener listenerNeg = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
//                getActivity().finish();
                dialog.dismiss();
            }
        };
        DialogInterface.OnClickListener listenerPos = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, 123);
                dialog.dismiss();
            }
        };

        if (showSettingScreen) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle(R.string.app_name)
                    .setMessage(R.string.no_camera_permission_showSetting)
                    .setPositiveButton(R.string.ok, listenerPos)
                    .setNegativeButton(R.string.cancel, listenerNeg)
                    .show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle(R.string.app_name)
                    .setMessage(R.string.no_camera_permission)
                    .setNegativeButton(R.string.ok, listenerNeg)
                    .show();
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // Check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                mContext);
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    public boolean getTouchEvent(MotionEvent e) {
        boolean b = scaleGestureDetector.onTouchEvent(e);
        boolean c = gestureDetector.onTouchEvent(e);
        return b || c || getTouchEvent(e);
    }

    public void wsCallProductDetails() {

        if (Utility.isNetworkAvailable(mContext)) {

            try {

                String userToken = tinyDB.getString(UserDefaults.USER_TOKEN);
                String encodeString = Utility.encode(tinyDB.getString(UserDefaults.ENCODE_KEY), tinyDB.getString(UserDefaults.ENCODE_KEY_IV), ((MainActivity) mContext).dtoUser.getGuid());

                Attribute attribute = new Attribute();
                attribute.setUser_id(String.valueOf(((MainActivity) mContext).dtoUser.getId()));
                attribute.setProduct_name(productName);
                attribute.setAccess_key(encodeString);
                attribute.setSecret_key(userToken);

                new WebserviceWrapper(mContext, attribute, ScanFragment.this, true, getString(R.string.Loading_msg))
                        .new WebserviceCaller()
                        .execute(WebserviceWrapper.WEB_CALLID.PRODUCT_DETAILS.getTypeCode());

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }

        } else {
            // noInternetConnection(getString(R.string.no_internet_connection));
        }

    }

    @Override
    public void onResponse(int apiCode, Object object, Exception error) {

        if (apiCode == WebserviceWrapper.WEB_CALLID.PRODUCT_DETAILS.getTypeCode()) {

            productName = "";
            //LifyzerFragment.productName = "";

            if (object != null) {
                try {
                    DTOProductDetailsData dtoProductDetailsData = (DTOProductDetailsData) object;
                    if (dtoProductDetailsData.getStatus().equalsIgnoreCase(UserDefaults.SUCCESS_STATUS)) {

                        if (dtoProductDetailsData.getProduct() != null && dtoProductDetailsData.getProduct().size() > 0) {

                            tinyDB.putBoolean(UserDefaults.NEED_REFRESH_HISTORY, true);

                            Intent intent = new Intent(mContext, ProductDetailsActivity.class);
                            intent.putExtra("productDetails", dtoProductDetailsData.getProduct().get(0));
                            startActivity(intent);

                            //Utility.showLongSnackBar(ll_parent, dtoProductDetailsData.getMessage(), mContext);
                        } else {
                            displayNoResultDialog();
                            //Utility.showLongSnackBar(ll_parent, dtoProductDetailsData.getMessage(), mContext);
                        }

                    } else {
                        Utility.showLongSnackBar(ll_parent, dtoProductDetailsData.getMessage(), mContext);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }
            }

        }

    }

    @Override
    public void onFlashLightToggle(boolean isFLashOn) {
        mCameraSource.setFlashMode(isFLashOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
        startCameraSource();

    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }

    public CameraSource getCameraSourse() {
        return mCameraSource;
    }

}
