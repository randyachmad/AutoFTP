package com.sabinsolusi.autoftp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.io.CopyStreamAdapter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Locale;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {
    private TextView txtStatus;
    private TextView stat1;
    private TextView stat2;
    private TextView stat3;
    private TextView stat4;
    private TextView stat5;
    private TextView stat6;
    private TextView stat7;
    private TextView txtProg;
    private ProgressBar progBar;
    private Button btnCancel;
    private Button btnUpload;
    private Button btnDownload;
    private GifImageView anim;

    private final String host = "10.100.0.254";
    private final int port = 21;
    private final String ftp_user = "digitalmr";
    private final String ftp_password = "Rsdk#DMR";

    private Handler mHandler = new Handler();

    private long mStartRX = 0;
    private long mStartTX = 0;

    private int mode = 0; //1=upload; 2=download;
    private boolean isTaskInProgress = false;
    private boolean isUDInProgress = false;

    private int seconds = 0;

    private AsyncTask bgTask;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        stat1 = findViewById(R.id.stat1);
        stat2 = findViewById(R.id.stat2);
        stat3 = findViewById(R.id.stat3);
        stat4 = findViewById(R.id.stat4);
        stat5 = findViewById(R.id.stat5);
        stat6 = findViewById(R.id.stat6);
        stat7 = findViewById(R.id.stat7);
        txtProg = findViewById(R.id.txtProg);
        progBar = findViewById(R.id.progBar);
        btnCancel = findViewById(R.id.btnCancel);
        btnUpload = findViewById(R.id.btnUpload);
        btnDownload = findViewById(R.id.btnDownload);
        anim = findViewById(R.id.anim);

        grantPermissions();

        btnUpload.setOnClickListener(v -> {
            mode = 1; //UPLOAD
            bgTask = new ftpUploadAsyncTask().execute(host, String.valueOf(port), ftp_user, ftp_password);
        });
        btnDownload.setOnClickListener(v -> {
            mode = 2; //DOWNLOAD
            bgTask = new ftpDownloadAsyncTask().execute(host, String.valueOf(port), ftp_user, ftp_password);
        });

        btnCancel.setOnClickListener(v -> {
            if(bgTask!=null){
                if (isTaskInProgress) {
                    bgTask.cancel(true);
                }

                if (bgTask.isCancelled()) {
                    isTaskInProgress = false;
                    isUDInProgress = false;

                    stat1.setText("0 KB/s");
                    stat5.setTextColor(Color.RED);
                    stat5.setText("User interrupted");

                    txtStatus.setText("Canceled");

                    anim.setVisibility(View.GONE);
                    txtProg.setVisibility(View.GONE);
                    progBar.setVisibility(View.GONE);
                    btnCancel.setVisibility(View.GONE);

                    btnUpload.setEnabled(true);
                    btnDownload.setEnabled(true);
                }
            }
        });

        mStartRX = TrafficStats.getTotalRxBytes();
        mStartTX = TrafficStats.getTotalTxBytes();

        ColorStateList defFontColor = stat1.getTextColors();
        if (mStartRX == TrafficStats.UNSUPPORTED || mStartTX == TrafficStats.UNSUPPORTED) {
            stat1.setTextColor(Color.RED);
            stat1.setText("This device doesn't supported");
        } else {
            stat1.setTextColor(defFontColor);
            mHandler.postDelayed(mRunnable, 1000);
        }

        runTimer();
    }

    private void runTimer() {
        // Creates a new Handler
        final Handler handler = new Handler();

        // Call the post() method,
        // passing in a new Runnable.
        // The post() method processes
        // code without a delay,
        // so the code in the Runnable
        // will run almost immediately.
        handler.post(new Runnable() {
            @Override

            public void run()
            {
                int hours = seconds / 3600;
                int minutes = (seconds % 3600) / 60;
                int secs = seconds % 60;

                // Format the seconds into hours, minutes,
                // and seconds.
                String time = String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);

                // Set the text view text.
                runOnUiThread(() -> {
                    stat6.setText(time);
                });

                // If running is true, increment the
                // seconds variable.
                if (isUDInProgress) {
                    seconds++;
                }

                // Post the code again
                // with a delay of 1 second.
                handler.postDelayed(this, 1000);
            }
        });
    }

    private final Runnable mRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        public void run() {
            if(mode==1){
                long txBytes = TrafficStats.getTotalTxBytes();
                long speed = txBytes - mStartTX;
                mStartTX = txBytes;
                if(isTaskInProgress) {
                    stat1.setText(humanReadableByteCountBin(speed) + "/s");
                }else{
                    stat1.setText("0 KB/s");
                }
            }else if(mode==2){
                long rxBytes = TrafficStats.getTotalRxBytes();
                long speed = rxBytes - mStartRX;
                mStartRX = rxBytes;
                if(isTaskInProgress) {
                    stat1.setText(humanReadableByteCountBin(speed) + "/s");
                }else{
                    stat1.setText("0 KB/s");
                }
            }

            mHandler.postDelayed(mRunnable, 1000);
        }
    };

    @SuppressLint("StaticFieldLeak")
    private class ftpUploadAsyncTask extends AsyncTask<String, Void, String> {
        FTPClient ftp = new FTPClient();
        boolean error = false;

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isTaskInProgress = true;

            stat5.setTextColor(Color.BLUE);
            stat5.setText("Connecting to the host");
            txtStatus.setText("Connecting...");

            btnCancel.setVisibility(View.VISIBLE);

            btnUpload.setEnabled(false);
            btnDownload.setEnabled(false);

            seconds = 0;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected String doInBackground(String... strings) {
            if(copyAssets(MainActivity.this)){
                try {
                    int reply;
                    String host = strings[0];
                    int port = Integer.parseInt(strings[1]);
                    String ftp_user = strings[2];
                    String ftp_password = strings[3];

                    File asset = new File(MainActivity.this.getFilesDir().getPath()+"/file_uploads", "testfile.zip");
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(asset));

                    runOnUiThread(() -> {
                        stat7.setText(host+":"+port);
                        stat2.setText(humanReadableByteCountBin(Long.parseLong(String.valueOf(asset.length()))));
                    });

                    ftp.setCopyStreamListener(new CopyStreamAdapter() {
                        @SuppressLint({"SetTextI18n", "NewApi"})
                        @Override
                        public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                            int percent = (int)(totalBytesTransferred*100/asset.length());
                            runOnUiThread(() -> {
                                txtProg.setText("["+totalBytesTransferred+"/"+asset.length()+"] -- "+percent + "%");
                                progBar.setProgress(percent, true);
                            });
                        }
                    });

                    ftp.connect(host, port);
                    ftp.login(ftp_user, ftp_password);

                    Log.e("X-DEBUG", "Connected to " + host + ":" + port + " => "+ftp.getReplyString());

                    reply = ftp.getReplyCode();

                    if(!FTPReply.isPositiveCompletion(reply)) {
                        ftp.disconnect();
                        Log.e("X-DEBUG", "FTP server refused connection.");

                        return "FTP server refused connection";
                    }

                    // transfer files
                    ftp.setFileTransferMode(FTPClient.STREAM_TRANSFER_MODE);
                    boolean cwd = ftp.changeWorkingDirectory("/test");
                    Log.e("X-DEBUG", "CHANGE WORKING DIR: "+cwd);

                    runOnUiThread(() -> {
                        txtProg.setText("[0/"+asset.length()+"] -- 0%");
                        stat5.setText("Upload in progress");
                        txtStatus.setText("Uploading...");
                        anim.setVisibility(View.VISIBLE);
                        txtProg.setVisibility(View.VISIBLE);
                        progBar.setVisibility(View.VISIBLE);
                        btnCancel.setVisibility(View.VISIBLE);

                        progBar.setProgress(0);
                    });

                    isUDInProgress = true;

                    boolean upStatus = ftp.storeFile("testfile.zip", bis);
                    Log.e("X-DEBUG", "UPLOAD STATUS: "+upStatus);

                    isUDInProgress = false;

                    ftp.logout();
                    ftp.disconnect();

                    if(!upStatus){
                        return "Upload failed";
                    }
                } catch(IOException e) {
                    error = true;
                    Log.e("X-DEBUG", "EXCEPTION: "+e.getLocalizedMessage());
                    e.printStackTrace();

                    return e.getLocalizedMessage();
                }

                return null;
            }else{
                Log.e("X-DEBUG", "FAILED TO COPYING ASSET FILE");

                return "Failed copying asset file";
            }
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if(isUDInProgress){
                isUDInProgress = false;
            }

            isTaskInProgress = false;

            if(s!=null){
                stat5.setText(s);
                stat5.setTextColor(Color.RED);
                txtStatus.setText("Upload Failed");
            }else{
                stat5.setText("Upload Finished");
                stat5.setTextColor(Color.GREEN);
                txtStatus.setText("Upload Success");
            }

            anim.setVisibility(View.GONE);
            txtProg.setVisibility(View.GONE);
            progBar.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);

            btnUpload.setEnabled(true);
            btnDownload.setEnabled(true);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ftpDownloadAsyncTask extends AsyncTask<String, Void, String> {
        FTPClient ftp = new FTPClient();
        boolean error = false;

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isTaskInProgress = true;

            stat5.setTextColor(Color.BLUE);
            stat5.setText("Connecting to the host");
            txtStatus.setText("Connecting...");

            btnCancel.setVisibility(View.VISIBLE);

            btnUpload.setEnabled(false);
            btnDownload.setEnabled(false);

            seconds = 0;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected String doInBackground(String... strings) {
            if(copyAssets(MainActivity.this)){
                try {
                    int reply;
                    String host = strings[0];
                    int port = Integer.parseInt(strings[1]);
                    String ftp_user = strings[2];
                    String ftp_password = strings[3];
                    byte data[] = new byte[1024 * 4];

                    runOnUiThread(() -> {
                        stat7.setText(host+":"+port);
                    });

                    ftp.connect(host, port);
                    ftp.login(ftp_user, ftp_password);

                    Log.e("X-DEBUG", "Connected to " + host + ":" + port + " => "+ftp.getReplyString());

                    reply = ftp.getReplyCode();

                    if(!FTPReply.isPositiveCompletion(reply)) {
                        ftp.disconnect();
                        Log.e("X-DEBUG", "FTP server refused connection.");

                        return "FTP server refused connection";
                    }

                    // transfer files
                    ftp.setFileTransferMode(FTPClient.STREAM_TRANSFER_MODE);
                    boolean cwd = ftp.changeWorkingDirectory("/test");
                    Log.e("X-DEBUG", "CHANGE WORKING DIR: "+cwd);

                    String remoteSize = ftp.getSize("testfile.zip");
                    Log.e("X-DEBUG", "REMOTE FILE SIZE: "+remoteSize);

                    ftp.setCopyStreamListener(new CopyStreamAdapter() {
                        @SuppressLint({"SetTextI18n", "NewApi"})
                        @Override
                        public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                            int percent = (int)(totalBytesTransferred*100/Long.parseLong(remoteSize));

                            runOnUiThread(() -> {
                                txtProg.setText("["+totalBytesTransferred+"/"+remoteSize+"] -- "+percent + "%");
                                progBar.setProgress(percent, true);
                            });
                        }
                    });

                    runOnUiThread(() -> {
                        txtProg.setText("[0/"+remoteSize+"] -- 0%");
                        stat2.setText(humanReadableByteCountBin(Long.parseLong(remoteSize)));
                        stat5.setText("Download in progress");
                        txtStatus.setText("Downloading...");
                        anim.setVisibility(View.VISIBLE);
                        txtProg.setVisibility(View.VISIBLE);
                        progBar.setVisibility(View.VISIBLE);
                        btnCancel.setVisibility(View.VISIBLE);

                        progBar.setProgress(0);
                    });

                    isUDInProgress = true;

                    InputStream bis = new BufferedInputStream(ftp.retrieveFileStream("testfile.zip"), 1024 * 8);
                    File outDir = new File(MainActivity.this.getFilesDir().getPath()+"/file_uploads");
                    File outFile = new File(MainActivity.this.getFilesDir().getPath()+"/file_uploads", "testfile.zip");

                    if(!outDir.exists()){
                        if(!outDir.mkdir()){
                           Log.e("X-DEBUG", "FAILED CREATE LOCAL PATH");
                        }
                    }

                    if(outFile.exists()){
                        if(!outFile.delete()){
                            Log.e("X-DEBUG", "FAILED DELETE EXISTING LOCAL FILE");
                        }
                    }

                    OutputStream output = new FileOutputStream(outFile);

                    int count;
                    while ((count = bis.read(data)) != -1) {
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    bis.close();

                    isUDInProgress = false;

                    if(bis.available()<=0){
                        return "Download failed";
                    }

                    ftp.logout();
                    ftp.disconnect();
                } catch(IOException e) {
                    error = true;
                    Log.e("X-DEBUG", "EXCEPTION: "+e.getLocalizedMessage());
                    e.printStackTrace();

                    return e.getLocalizedMessage();
                }

                return null;
            }else{
                Log.e("X-DEBUG", "FAILED TO COPYING ASSET FILE");

                return "Failed copying asset file";
            }
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if(isUDInProgress){
                isUDInProgress = false;
            }

            isTaskInProgress = false;

            if(s!=null){
                stat5.setText(s);
                stat5.setTextColor(Color.RED);
                txtStatus.setText("Download Failed");
            }else{
                stat5.setText("Download Finished");
                stat5.setTextColor(Color.GREEN);
                txtStatus.setText("Download Success");
            }

            anim.setVisibility(View.GONE);
            txtProg.setVisibility(View.GONE);
            progBar.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);

            btnUpload.setEnabled(true);
            btnDownload.setEnabled(true);
        }
    }

    private void grantPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        100);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    private void ftpConnect(){
        FTPClient ftp = new FTPClient();

        boolean error = false;
        try {
            int reply;
            String server = "10.100.0.254";
            ftp.connect(server);
            ftp.login("digitalmr", "Rsdk#DMR");

//            Toast.makeText(this, "Connected to \" + server + \"", Toast.LENGTH_LONG).show();

            Log.e("X-DEBUG", "Connected to " + server + ": "+ftp.getReplyString());

//            txtStatus.setText("Connected to " + server + ": "+ftp.getReplyString());
            // After connection attempt, you should check the reply code to verify
            // success.
            reply = ftp.getReplyCode();

            if(!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
//                Toast.makeText(this, "FTP server refused connection.", Toast.LENGTH_LONG).show();
                Log.e("X-DEBUG", "FTP server refused connection.");
//                txtStatus.setText("FTP server refused connection.");
//                System.exit(1);
            }

            File asset = new File(MainActivity.this.getFilesDir().getPath()+"/file_uploads", "testfile.zip");
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(asset));

            // transfer files
            ftp.setFileTransferMode(FTPClient.STREAM_TRANSFER_MODE);
            boolean cwd = ftp.changeWorkingDirectory("/test");
            Log.e("X-DEBUG", "CHANGE WORKING DIR: "+cwd);

            boolean upStatus = ftp.storeFile("testfile.zip", bis);
            Log.e("X-DEBUG", "UPLOAD STATUS: "+upStatus);

//            ftp.logout();
        } catch(IOException e) {
            error = true;
            Log.e("X-DEBUG", "EXCEPTION: "+e.getLocalizedMessage());
            e.printStackTrace();
        }

//        if(ftp.isConnected()) {
//            try {
//                ftp.disconnect();
//            } catch(IOException ioe) {
//                Log.e("X-DEBUG", "EXCEPTION: "+ioe.getLocalizedMessage());
//            }
//        }
//        System.exit(error ? 1 : 0);

        Log.e("X-DEBUG", "CONCLUSIONS: "+(error ? "Error to connect" : "Connected"));
    }

    private void ftpsConnect() throws JSchException, SftpException, FileNotFoundException {
        JSch jsch = new JSch();

//        String knownHostsFilename = "/Users/macbook/.ssh/known_hosts";
//        jsch.setKnownHosts( knownHostsFilename );

        Session session = jsch.getSession( "root", "dev.sabinsolusi.com" );
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword( "Sabin2018#" );
        session.connect();

        Channel channel = session.openChannel( "sftp" );
        channel.connect();

        ChannelSftp sftpChannel = (ChannelSftp) channel;

//        sftpChannel.get("remote-file", "local-file" );
// OR
//        InputStream in = sftpChannel.get( "remote-file" );
        // process inputstream as needed

        File asset = new File(MainActivity.this.getFilesDir().getPath()+"/file_uploads", "testfile.zip");

        InputStream is = new FileInputStream(asset);

        sftpChannel.put(is, "commons-net-3.8.0-bin.zip", new SftpProgressMonitor() {
            @Override
            public void init(int i, String s, String s1, long l) {
                Log.e("X-DEBUG", "UPLOAD TO '"+s1+"' BEGIN");
            }

            @Override
            public boolean count(long l) {
                Log.e("X-DEBUG", "UPLOAD FILE SIZE: "+l+" bytes");
                return false;
            }

            @Override
            public void end() {
                Log.e("X-DEBUG", "UPLOAD FINISH");
            }
        }, ChannelSftp.OVERWRITE);

        sftpChannel.exit();
        session.disconnect();
    }

    private boolean copyAssets(Context context){
        Log.i("Test", "Setup::copyResources");
        InputStream in = context.getResources().openRawResource(R.raw.testfile);

        try {
            String filename = "testfile.zip";
            String path = MainActivity.this.getFilesDir().getPath()+"/file_uploads";
            File filePath = new File(path);
            Log.e("X-DEBUG", "PATH: "+filePath.getPath());

            if(!filePath.exists() || !filePath.isDirectory()){
                if(!filePath.mkdir()) {
                    Log.e("X-DEBUG", "FAILED TO CREATE DATA DIR");

                    return false;
                }
            }

            File internFile = new File(path, filename);
            if(internFile.exists()){
                Log.e("X-DEBUG", "FILE EXIST");

                if(internFile.delete()){
                    Log.e("X-DEBUG", "FILE DELETED");
                }
            }

            OutputStream out = new FileOutputStream(new File(path, filename));
            byte[] buffer = new byte[1024];
            int len;
            while((len = in.read(buffer, 0, buffer.length)) != -1){
                out.write(buffer, 0, len);
            }
            in.close();
            out.close();

            return true;
        } catch (IOException e) {
            Log.e("X-DEBUG", "IO-EXCEPTION: "+e.getMessage());

            return false;
        }
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %cB", value / 1024.0, ci.current());
    }
}