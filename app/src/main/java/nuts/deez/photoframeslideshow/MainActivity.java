package nuts.deez.photoframeslideshow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.icu.text.SimpleDateFormat;
import androidx.exifinterface.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    ImageView image;
    Bitmap lastBitmap = null;
    WakeLock keepAlive = null;

    private static final String TAG = "PhotoFrameSlideshow";

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    String imageFolder = Environment.getExternalStorageDirectory().toString() + "/Pictures/";
    String configFilename = "config.json";

    int imageUpdateTime = 15; //Seconds
    int ntpUpdateTime   = 900; //Seconds
    boolean shuffleImages = true;
    boolean showFileNames = true;
    int activeHourStart = 7;
    int activeHourEnd = 23;
    boolean active = true;
    String ntpServer = "";
    boolean forceClockSync = false;

    int frameWidth = 1920;
    int frameHeight = 1080;

    Handler updateImageHandler;
    Handler updateNtpHandler;
    boolean hideUi = true;
    boolean pause = false;
    int imageIndex = -1;

    List<String> picturesRemaining = new ArrayList<>();
    List<String> allPictures = new ArrayList<>();

    List<String> allowedFileExtensions = new ArrayList<>();
    List<String> subdirectories = new ArrayList<>();

    private final Runnable updateImageRunnable = new Runnable() {
        @Override
        public void run() {
            updateImage();
            updateImageHandler.postDelayed(this, imageUpdateTime * 1000L);
        }
    };

    private final Runnable updateNtpRunnable = new Runnable() {
        @Override
        public void run() {
            new GetNTPTimeBackground().execute();
            updateNtpHandler.postDelayed(this, ntpUpdateTime * 1000L);
        }
    };


    @SuppressLint({"ClickableViewAccessibility", "WakelockTimeout"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Wakelock used to keep time updates and everything going overnight
        keepAlive = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeepAlive WakeLock:");
        keepAlive.acquire();

        image = findViewById(R.id.imageView);
        image.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()){
            @Override
            public void onSwipeLeft() {
                //Toast.makeText(getApplicationContext(), "Swipe Left", Toast.LENGTH_SHORT).show();
                //Go to next image (And reset timer)
                goToNextImage();
            }

            @Override
            public void onSwipeRight() {
                //Toast.makeText(getApplicationContext(), "Swipe Right", Toast.LENGTH_SHORT).show();
                //Go to previous image (and reset timer)
                goToPreviousImage();
            }

            @Override
            public void onSwipeUp() {
                //Toast.makeText(getApplicationContext(), "Swipe Up", Toast.LENGTH_SHORT).show();
                //Rotate image counter clockwise
                rotatePicture(false);
            }

            @Override
            public void onSwipeDown() {
                //Toast.makeText(getApplicationContext(), "Swipe Down", Toast.LENGTH_SHORT).show();
                //Rotate image clockwise
                rotatePicture(true);
            }

            @Override
            public void onScreenTap() {
                //Toast.makeText(getApplicationContext(), "Tap", Toast.LENGTH_SHORT).show();
                pauseUnpauseImages();
            }

            @Override
            public void onDoubleScreenTap() {
                //Toast.makeText(getApplicationContext(), "Double Tap", Toast.LENGTH_SHORT).show();
                hideUi = !hideUi;
                updateFlagsAndStuff();
            }

        });

        updateFlagsAndStuff();

        int permission = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE);
        }

        allowedFileExtensions.add(".png");
        allowedFileExtensions.add(".jpg");
        allowedFileExtensions.add(".jpeg");
        allowedFileExtensions.add(".bmp");

        frameWidth = getResources().getDisplayMetrics().widthPixels;
        frameHeight = getResources().getDisplayMetrics().heightPixels;
        Log.i(TAG, "Frame is " + frameWidth + " by " + frameHeight);

        imageIndex = -1;

        loadConfigFile();
        if (savedInstanceState!=null){
            //App is being reloaded

            imageIndex = savedInstanceState.getInt("imageIndex");
            if (imageIndex>=0){
                imageIndex--;
            }
            picturesRemaining = savedInstanceState.getStringArrayList("picturesRemaining");
            pause = savedInstanceState.getBoolean("pause");
            active = savedInstanceState.getBoolean("pause");
            if (pause){
                updateImage(); //Since we won't get called by the periodic timer, manually call here to draw the image
            }
        }

        updateImageHandler = new Handler();

        if (!pause) {
            updateImageHandler.post(updateImageRunnable);
        }


        updateNtpHandler = new Handler();
        updateNtpHandler.post(updateNtpRunnable);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Getting destroyed - probably a rotation?");
        if (lastBitmap != null) {
            lastBitmap.recycle();
        }
        lastBitmap = null;
        updateImageHandler.removeCallbacks(updateImageRunnable);
        updateNtpHandler.removeCallbacks(updateNtpRunnable);
        keepAlive.release();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt("imageIndex",imageIndex);
        outState.putStringArrayList("picturesRemaining",(ArrayList<String>)picturesRemaining);
        outState.putBoolean("pause",pause);
        outState.putBoolean("active",active);
    }

    void pauseUnpauseImages(){
        pause = !pause;

        if (pause){
            Toast.makeText(getApplicationContext(), "Paused", Toast.LENGTH_SHORT).show();
            updateImageHandler.removeCallbacks(updateImageRunnable);
        } else {
            Toast.makeText(getApplicationContext(), "Unpaused", Toast.LENGTH_SHORT).show();
            updateImageHandler.postDelayed(updateImageRunnable, imageUpdateTime * 1000L);
        }
    }

    void delayImageUpdate(){
        updateImageHandler.removeCallbacks(updateImageRunnable);
        updateImageHandler.postDelayed(updateImageRunnable, imageUpdateTime * 1000L);
    }

    void createDefaultConfig(FileWriter fw) {
        JSONObject config = new JSONObject();
        try {
            config.put("imageTime", 60);
            config.put("shuffle", true);
            config.put("showFileNames", true);
            config.put("activeHourStart", 7);
            config.put("activeHourEnd", 23);
            config.put("ntpServer","pool.ntp.org");
            config.put("forceClockSync",false);
            config.put("ntpSyncInterval",3600);

            JSONArray fileTypes = new JSONArray();
            fileTypes.put(".png");
            fileTypes.put(".jpg");
            fileTypes.put(".jpeg");
            fileTypes.put(".bmp");

            config.put("fileTypes", fileTypes);

            JSONArray subdirs = new JSONArray();
            JSONObject subdirInfo = new JSONObject();
            subdirInfo.put("folder", "example1");
            subdirInfo.put("enabled", false);
            subdirs.put(subdirInfo);

            subdirInfo = new JSONObject();
            subdirInfo.put("folder", "example2");
            subdirInfo.put("enabled", false);
            subdirs.put(subdirInfo);

            config.put("subdirectories", subdirs);

            fw.write(config.toString(4));
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Couldn't generate default config file...", Toast.LENGTH_SHORT).show();
        }

    }

    void loadConfigFile() {
        File configFile = new File(imageFolder + configFilename);

        if (configFile.exists()) {
            StringBuilder text = new StringBuilder();

            try {
                BufferedReader br = new BufferedReader(new FileReader(configFile));
                String line;

                while ((line = br.readLine()) != null) {
                    text.append(line);
                    text.append('\n');
                }
                br.close();
                //Toast.makeText(getApplicationContext(), "Loaded config file!", Toast.LENGTH_SHORT).show();

                JSONObject configJson = new JSONObject(text.toString());

                imageUpdateTime = configJson.getInt("imageTime");
                //Toast.makeText(getApplicationContext(), "Image update time is now "+imageUpdateTime, Toast.LENGTH_SHORT).show();
                shuffleImages = configJson.getBoolean("shuffle");

                showFileNames = configJson.getBoolean("showFileNames");

                activeHourStart = configJson.getInt("activeHourStart");
                activeHourEnd = configJson.getInt("activeHourEnd");

                ntpServer = configJson.getString("ntpServer");
                forceClockSync = configJson.getBoolean("forceClockSync");
                ntpUpdateTime = configJson.getInt("ntpSyncInterval");

                JSONArray fileTypesJson = configJson.getJSONArray("fileTypes");
                allowedFileExtensions = new ArrayList<>();
                for (int i = 0; i < fileTypesJson.length(); i++) {
                    allowedFileExtensions.add(fileTypesJson.getString(i));
                }

                JSONArray subdirsJson = configJson.getJSONArray("subdirectories");
                subdirectories = new ArrayList<>();
                for (int i = 0; i < subdirsJson.length(); i++) {
                    JSONObject subdirInfo = subdirsJson.getJSONObject(i);
                    boolean enabled = subdirInfo.getBoolean("enabled");
                    String folder = subdirInfo.getString("folder");

                    if (enabled) {
                        subdirectories.add(folder);
                    }

                }

                //Load file types into the real list


            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Couldn't read config file...", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Toast.makeText(getApplicationContext(), "Couldn't parse config file...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Some other failure...", Toast.LENGTH_SHORT).show();
            }

        } else {
            //Create new default config file
            try {
                if (configFile.createNewFile()) {
                    FileWriter fw = new FileWriter(configFile);
                    createDefaultConfig(fw);
                    fw.close();
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Couldn't create config file...", Toast.LENGTH_SHORT).show();
            }
        }

    }

    boolean isFileImage(String filename) {
        if (filename.startsWith(".")) {
            return false;
        }

        boolean fileTypeMatch = false;

        for (int i = 0; i < allowedFileExtensions.size() && !fileTypeMatch; i++) {
            if (filename.toLowerCase().endsWith(allowedFileExtensions.get(i))) {
                fileTypeMatch = true;
            }
        }
        return fileTypeMatch;
    }

    void loadImageList() {
        File directory = new File(imageFolder);
        File[] files = directory.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                String filename = file.getName();
                if (isFileImage(filename)) {
                    if (!allPictures.contains(imageFolder + filename)) {
                        allPictures.add(imageFolder + filename);
                    }
                }
            }
        }

        for (String folder : subdirectories) {
            Log.i(TAG, "Loading images from " + folder);
            directory = new File(imageFolder + folder);
            files = directory.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    String filename = file.getName();
                    if (isFileImage(filename)) {
                        if (!allPictures.contains(imageFolder + folder + "/" + filename)) {
                            allPictures.add(imageFolder + folder + "/" + filename);
                        }
                    }
                }
            }

        }

        //Toast.makeText(getApplicationContext(), "Reloaded "+allPictures.size()+" images", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Reloaded " + allPictures.size() + " images");
    }

    void refillRemainingPictures() {
        allPictures = new ArrayList<>();
        loadImageList();
        picturesRemaining = new ArrayList<>(allPictures);

        //Shuffle if desired
        if (shuffleImages) {
            Collections.shuffle(picturesRemaining);
        } else {
            Collections.sort(picturesRemaining, String::compareToIgnoreCase);
        }
    }

    void updateImage() {
        loadConfigFile();
        updateFlagsAndStuff();

        if (active) {
            imageIndex++;
            if (imageIndex >= picturesRemaining.size()) {
                refillRemainingPictures();
                imageIndex = 0;
            }

            Log.i(TAG,"Image index is now "+imageIndex);

            if (imageIndex < picturesRemaining.size()) {
                String nextPic = picturesRemaining.get(imageIndex);
                if (showFileNames) {
                    Toast.makeText(getApplicationContext(), nextPic, Toast.LENGTH_SHORT).show();
                }
                showPictureAsync(nextPic);

            } else {
                Toast.makeText(getApplicationContext(), "No Pictures Found!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void goToPreviousImage(){
        if (imageIndex == 0){ //If it's showing the first picture...
             //Currently showing first picture, can't go back
            Toast.makeText(getApplicationContext(), "Cannot scroll further back!", Toast.LENGTH_SHORT).show();
        } else {
            if(!pause) { //Stay paused if already paused...
                delayImageUpdate();
            }
            imageIndex-=2; //Have to skip back two, since it increments in updateImage
            updateImage();
        }
    }

    void goToNextImage(){
        if (imageIndex == picturesRemaining.size()){ //If it's showing the last picture...
            //Currently showing last picture, can't go forward
            Toast.makeText(getApplicationContext(), "Cannot scroll further forward!", Toast.LENGTH_SHORT).show();
        } else {
            if (!pause) { //Stay paused if already paused...
                delayImageUpdate();
            }
            updateImage(); //Will automatically progress to next image
        }

    }

    void rotateExifValue(String filename,boolean clockwise){
        try {
            ExifInterface ei = new ExifInterface(filename);

            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);

            if (clockwise) {
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_180));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_270));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                        break;
                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                        break;
                }
            } else {
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_180));
                        break;
                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        ei.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_270));
                        break;
                }
            }
            ei.saveAttributes();
        } catch(Exception e){
            e.printStackTrace();
        }

    }

    void rotatePicture(boolean clockwise){
        if(!pause){
            delayImageUpdate();
        }

        if(clockwise){
            Toast.makeText(getApplicationContext(), "Rotating Clockwise", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Rotating Counter-Clockwise", Toast.LENGTH_SHORT).show();
        }
        rotateExifValue(picturesRemaining.get(imageIndex),clockwise);
        showPictureAsync(picturesRemaining.get(imageIndex));

    }

    @SuppressLint("WakelockTimeout")
    void updateFlagsAndStuff() {
        Window window = getWindow();
        Calendar cal = Calendar.getInstance();
        Date curTime = cal.getTime();
        int curHour = curTime.getHours();

        if (window != null) {
            //Check if screen should still be on
            if (curHour >= activeHourStart && curHour < activeHourEnd) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.i(TAG, "Current hour is inside active hours - " + curHour);
                if (!active) {
                    //Toast.makeText(getApplicationContext(), "Active Hours have begun", Toast.LENGTH_SHORT).show();


                    //SCREEN_BRIGHT_WAKE_LOCK is needed to actually turn the screen on
                    //(Maybe there's something better that isn't deprecated?)
                    @SuppressLint("InvalidWakeLockTag")
                    WakeLock screenLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");
                    screenLock.acquire();
                    Log.i(TAG, "Should have just made screen wake up");
                    screenLock.release();
                }
                active = true;
            } else {
                //To turn "keep screen on" off
                Log.i(TAG, "Current hour is outside active hours - " + curHour);
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                active = false;
            }
            View decorView = window.getDecorView();
            if (decorView != null) {
                int uiOptions = 0;
                if (hideUi) {
                    uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
                decorView.setSystemUiVisibility(uiOptions);
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    public static Bitmap decodeFile(File f, int WIDTH, int HEIGHT) {
        try {
            //Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f), null, o);

            //Find the correct scale value. It should be the power of 2.
            int scale = 1;
            while (o.outWidth / scale / 2 >= WIDTH && o.outHeight / scale / 2 >= HEIGHT)
                scale *= 2;

            Log.i(TAG, "Image scaling factor was " + scale);

            //Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    void showPictureAsync(String imageName) {
        new ShowImageBackground().execute(imageName);
    }

    @SuppressLint({"NewApi", "SimpleDateFormat"})
    private void setSystemClock(Date date){
        SimpleDateFormat dateFormat;
        //Android Studio claims this needs API Level 24, but nothing here requires more than API
        //Level 1, as far as I can see???  Works just fine on my API Level 23 device...
        dateFormat = new SimpleDateFormat("MMddHHmmyy.ss");

        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());

            String command = "date " + dateFormat.format(date) + "\n";

            os.writeBytes(command);
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            Log.i(TAG,"System time synced to NTP");
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            Log.e(TAG,e.toString());
        }

    }

    private  class ShowImageBackground extends AsyncTask<String,Integer,Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            image = findViewById(R.id.imageView);
            String imageName = params[0];
            File imageFile = new File(imageName);

            if (!imageFile.exists()) {
                Log.i(TAG, imageName + " does not exist?");
                return null;
            }

            try {
                ExifInterface ei = new ExifInterface(imageName);
                //Bitmap bmImg = BitmapFactory.decodeFile(imageFolder + imageName);
                Bitmap bmImg = decodeFile(imageFile, frameWidth, frameHeight);
                if (bmImg == null) {
                    Toast.makeText(getApplicationContext(), "Loaded null...", Toast.LENGTH_LONG).show();
                }

                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);

                switch (orientation) {

                    case ExifInterface.ORIENTATION_ROTATE_90:
                        bmImg = rotateImage(bmImg, 90);
                        Log.i(TAG, imageName+" is Rotated 90...");
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        bmImg = rotateImage(bmImg, 180);
                        Log.i(TAG, imageName+" is Rotated 180...");
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        bmImg = rotateImage(bmImg, 270);
                        Log.i(TAG, imageName+" is Rotated 270...");
                        break;

                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        Log.i(TAG, imageName+" is not Rotated...");
                        break;
                }

                return bmImg;
                //Toast.makeText(getApplicationContext(),imageName,Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed somehow!");
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            image.setImageBitmap(result);
            if (lastBitmap != null) {
                lastBitmap.recycle();
            }
            lastBitmap = result;
        }
    }

    private class GetNTPTimeBackground extends AsyncTask<String,Integer,Date> {
        @Override
        protected Date doInBackground(String... params) {
            SntpClient client = new SntpClient();
            long nowAsPerDeviceTimeZone;

            if (ntpServer.equals("")){
                return null;
            }

            if (client.requestTime(ntpServer,30000)){
                nowAsPerDeviceTimeZone = client.getNtpTime();

                Calendar cal = Calendar.getInstance();
                TimeZone timeZoneInDevice = cal.getTimeZone();
                int differentialOfTimeZones = timeZoneInDevice.getOffset(System.currentTimeMillis());
                nowAsPerDeviceTimeZone += differentialOfTimeZones;
                return new Date(nowAsPerDeviceTimeZone);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Date result) {
            if (result!=null) {
                if (forceClockSync){
                    setSystemClock(result);

                }
            } else {
                if (!ntpServer.equals("")) {
                    Log.e(TAG, "Failed to get a time from NTP");
                }
            }
        }
    }
}