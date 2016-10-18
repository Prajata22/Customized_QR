package io.github.scola.qart;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;

import io.github.scola.cuteqr.CuteR;

public class QRCodeResultActivity extends AppCompatActivity {
    private final static String TAG = "QRCodeResultActivity";

    private String qrText;
    private String ssText;
    private TextView mTextView;
    private TextView mSsTextView;
    private ImageView mQRImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qr_result_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.qr_result);

        mTextView = (TextView) findViewById(R.id.qr_text);
        mQRImage = (ImageView) findViewById(R.id.qr_result_img);
        mSsTextView = (TextView) findViewById(R.id.ss_text);

        qrText = getIntent().getExtras().getString("TEXT", "");
        if (qrText.isEmpty()) {
            mTextView.setVisibility(View.INVISIBLE);
        } else {
            mQRImage.setImageBitmap(CuteR.ProductNormal(qrText, false, Color.BLACK));
            mTextView.setOnClickListener(getClickListener(qrText, getResources().getStringArray(R.array.qr_list_uri)));
            mTextView.setText(qrText);
            if (qrText.startsWith("ss://")) {
                String ss = qrText.replace("ss://","");
                try {
                    ssText = "ss://" + new String(Base64.decode(ss, Base64.DEFAULT));
                    if (ssText.contains("@") && ssText.split(":").length == 4) {
                        mSsTextView.setVisibility(View.VISIBLE);
                        mSsTextView.setText(ssText);
                        mSsTextView.setOnClickListener(getClickListener(ssText, getResources().getStringArray(R.array.ss_list_uri)));
                    }
                } catch (IllegalArgumentException ex) {
                    ex.printStackTrace();
                }
            }

        }

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
    }

    private View.OnClickListener getClickListener (final String text, final String[] items) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v){
                AlertDialog.Builder builder = new AlertDialog.Builder(QRCodeResultActivity.this);
                builder.setTitle(text);
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        handTextAction(i, text);
                        dialogInterface.dismiss();
                    }
                });
                builder.show();
            }
        };
    }

    private void handTextAction(int pos, String text) {
        switch (pos) {
            case 0:
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("label", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.qr_copied, Toast.LENGTH_SHORT).show();
                break;
            case 1:
                Intent data = new Intent();
                data.putExtra("import", text);
                setResult(RESULT_OK, data);
                finish();
                break;
            case 2:
                Intent i = new Intent(android.content.Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, getResources().getString(R.string.share_channel)));
                break;
            case 3:
                Uri uri = Uri.parse(text);;
                String url = text.toLowerCase();
                if (url.contains("http://") || url.contains("https://")) {
                    Matcher m = Patterns.WEB_URL.matcher(url);
                    if (m.find()) {
                        url = m.group();
                        Log.d(TAG, "find url: " + url);
                        uri = Uri.parse(url);
                    } else {
                        Log.w(TAG, "No url found");
                    }
                }

                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Log.e(TAG, "Can not find activity to open the text");
                    Toast.makeText(this, R.string.no_activity, Toast.LENGTH_LONG).show();
                }

                break;
            case 4:
                File downloadFolder = new File(Environment.getExternalStorageDirectory(), "Download");
                if (!downloadFolder.exists()) {
                    downloadFolder.mkdirs();
                }

                File ssConfig = new File(downloadFolder, "ss_"+ text.split("@")[1].replace(":", "_") + ".json");
                saveSSConfig(ssConfig, text);
                Toast.makeText(this, getResources().getString(R.string.saved) + ssConfig.getAbsolutePath(), Toast.LENGTH_LONG).show();

                break;
            case 5:
                File shareFile = new File(getExternalCacheDir(), "ss_"+ text.split("@")[1].replace(":", "_") + ".json");
                saveSSConfig(shareFile, text);

                Uri contentUri = Uri.fromFile(shareFile);

                if (contentUri != null) {
                    Log.d(TAG, "Uri: " + contentUri);
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setType("application/json");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.share_via)));
                }
                break;

            default:
                break;
        }
    }

    private String genShadowsocksConfig(String text) throws JSONException {
        text = text.replace("ss://", "");
        String[] secretServer = text.split("@");
        String[] secret = secretServer[0].split(":");
        String[] serverPort = secretServer[1].split(":");

        JSONObject json = new JSONObject();

        json.put("server", serverPort[0]);
        json.put("server_port", Integer.parseInt(serverPort[1]));
        json.put("local_port", 1080);
        json.put("method", secret[0]);
        json.put("password", secret[1]);
        json.put("timeout", 300);

        String configStr = json.toString();
        String newStr = configStr.replaceFirst("\\{", "{\n\t");
        newStr = newStr.replaceAll(",\"", ",\n\t\"");
        newStr = newStr.substring(0, newStr.length() - 1) + "\n}";

        return newStr;
    }

    private void saveSSConfig(File dst, String text) {
        try {
            Util.saveConfig(dst, genShadowsocksConfig(text));
        } catch (JSONException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

}