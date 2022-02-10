package com.example.lectorqr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.transition.Transition;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.blikoon.qrcodescanner.QrCodeActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_QR_SCAN = 101;
    private PermissionStatus permissionStatus;
    private ProgressDialog pd;
    private DownloadImage di;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionStatus = new PermissionStatus(MainActivity.this);
        permissionStatus.confirmPermissionMsg();

        pd = new ProgressDialog(MainActivity.this);
        pd.setMessage("Downloading image, please wait ...");
        pd.setIndeterminate(true);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setCancelable(false);
        pd.setProgressNumberFormat("%1d KB/%2d KB");

        pd.setOnCancelListener(dialog -> di.cancel(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(permissionStatus != null)
            permissionStatus.confirmPermissionMsg();
    }

    public void onClick(View v) {
        Intent i = new Intent(MainActivity.this, QrCodeActivity.class);
        startActivityForResult(i, REQUEST_CODE_QR_SCAN);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(getApplicationContext(), "No se pudo obtener una respuesta", Toast.LENGTH_SHORT).show();
            String resultado = data.getStringExtra("com.blikoon.qrcodescanner.error_decoding_image");
            if (resultado != null) {
                Toast.makeText(getApplicationContext(), "No se pudo escanear el código QR", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == REQUEST_CODE_QR_SCAN) {
            if (data != null) {
                String lectura = data.getStringExtra("com.blikoon.qrcodescanner.got_qr_scan_relult");
                Toast.makeText(getApplicationContext(), "Colocado correctamente", Toast.LENGTH_SHORT).show();
                setWallpaper(lectura);
                di = new DownloadImage(MainActivity.this);
                di.execute(lectura);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setWallpaper(final String urlImage) {
        try {
            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(urlImage);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    Bitmap myImage = BitmapFactory.decodeStream(input);
                    WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
                    //wallpaperManager.setBitmap(myImage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.start();

        } catch (Exception error) {
            Log.e("Loading Image", "Error : " + error.getMessage());
        }
    }

    private class DownloadImage extends AsyncTask<String, Integer, String> {

        private Context c;

        private DownloadImage(Context c) {
            this.c = c;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream is = null;
            OutputStream os = null;
            HttpURLConnection con = null;
            int length;
            try {
                URL url = new URL(sUrl[0]);
                con = (HttpURLConnection) url.openConnection();
                con.connect();

                if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "HTTP CODE: " + con.getResponseCode() + " " + con.getResponseMessage();
                }

                length = con.getContentLength();

                pd.setMax(length / (1000));

                File dirImg = new File(Environment.getExternalStorageDirectory() + "/imgApp");
                if(!dirImg.exists())
                    dirImg.mkdirs();
                String CurrentDateAndTime = getCurrentDateAndTime();
                is = con.getInputStream();
                os = new FileOutputStream(Environment.getExternalStorageDirectory() + "/imgApp" + File.separator + CurrentDateAndTime + ".jpg");

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = is.read(data)) != -1) {
                    if (isCancelled()) {
                        is.close();
                        return null;
                    }
                    total += count;
                    if (length > 0) {
                        publishProgress((int) total);
                    }
                    os.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (os != null)
                        os.close();
                    if (is != null)
                        is.close();
                } catch (IOException ioe) {
                }

                if (con != null)
                    con.disconnect();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            pd.setIndeterminate(false);
            pd.setProgress(progress[0] / 1000);
        }

        @Override
        protected void onPostExecute(String result) {
            pd.dismiss();
            if (result != null) {
                Toast.makeText(c, "Download error: " + result, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(c, "Image downloaded successfully!", Toast.LENGTH_SHORT).show();
            }
        }

        private String getCurrentDateAndTime() {
            Calendar c = Calendar.getInstance();
            @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-­ss");
            String formattedDate = df.format(c.getTime());
            return formattedDate;
        }
    }
}