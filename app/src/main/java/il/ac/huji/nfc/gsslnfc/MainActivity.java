package il.ac.huji.nfc.gsslnfc;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/* ==================================================================================== */

/***
 * Based on codes from:
 * http://www.developer.com/ws/android/nfc-programming-in-android.html
 * http://code.tutsplus.com/tutorials/reading-nfc-tags-with-android--mobile-17278
 */

/* ==================================================================================== */

public class MainActivity extends Activity {

    /* ==================================================================================== */

    private TextView txtNfcSupported, txtNfcEnabled, txtAction, txtTech, txtNfcContent;
    private NfcAdapter mNfcAdapter;
    private static final String MIME_TEXT_PLAIN = "text/plain";
    private static final String TAG = "gsslNFC";

    /* ==================================================================================== */

    // TODO - discuss AndroidManifest.xml and Activity Chooser
    // TODO - (1) Influence of changes in nfc_tech_filter.xml
    // TODO - (2) Influence of changes in AndroidManifest.xml

    /* ==================================================================================== */

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_main);

        // Find the textViews
        txtNfcSupported = (TextView) findViewById(R.id.txtNfcSupported);
        txtNfcEnabled   = (TextView) findViewById(R.id.txtNfcEnabled);
        txtAction       = (TextView) findViewById(R.id.txtAction);
        txtTech         = (TextView) findViewById(R.id.txtTech);
        txtNfcContent   = (TextView) findViewById(R.id.txtNfcContent);

        // We can interact with the hardware via the NfcAdapter class
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            txtNfcSupported.setText("No");
            return;
        } else {
            txtNfcSupported.setText("Yes");
        }

        // Check if NFC is enabled
        txtNfcEnabled.setText((mNfcAdapter.isEnabled()) ? "Yes" : "No");

        // TODO - discuss what the following line is good for
        handleIntent(getIntent());
    }

    /* ==================================================================================== */

    @Override
    protected void onResume() {
        super.onResume();
        if (mNfcAdapter != null) {
            // Override the regular activity lifecycle callbacks and add logic to enable and
            // disable the foreground dispatch when the activity regains focus.
            setupForegroundDispatch(this, mNfcAdapter);
        }
    }
    /* ==================================================================================== */
    /* ==================================================================================== */
    /* ============================= Foreground Dispatch System============================ */
    /* ==================================================================================== */
    /* ==================================================================================== */

    // Why do we need it?
    // When our app is already opened and we attach the tag again,
    // the app is opened a second time instead of delivering the tag directly.
    // We bypass the problem by using a Foreground Dispatch -
    //  Instead of creating a new activity, onNewIntent will be called.

    @Override
    public void onPause() {
        // Stop the foreground dispatch.
        if (mNfcAdapter != null) {
            // Override the regular activity lifecycle callbacks and add logic to enable and
            // disable the foreground dispatch when the activity loses focus.
            mNfcAdapter.disableForegroundDispatch(this);
        }
        super.onPause();
    }

    /* ==================================================================================== */

    /**
     * The foreground dispatch system allows an activity to intercept an intent and claim priority
     * over other activities that handle the same intent.
     * Using this system involves constructing a few data structures for the Android
     * system to be able to send the appropriate intents to your application
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {

        // We want to use enableForegroundDispatch,
        // So we must first declare three things:

        // (1) Create a PendingIntent object so the Android system can populate it with
        // the details of the tag when it is scanned
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//                       getActivity(Context context, int requestCode, Intent intent, int flags) {
        final PendingIntent mPendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        // (2) Set an intent filter for all MIME data:
        // Declare intent filters to handle the intents that we want to intercept.
        // The foreground dispatch system checks the specified intent filters with the intent that
        // is received when the device scans a tag. If it matches, then your application handles the intent.
        IntentFilter ndefIntent = new IntentFilter();
        ndefIntent.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        ndefIntent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            // all MIME data
            ndefIntent.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }
        IntentFilter[] mIntentFilters = new IntentFilter[]{ndefIntent};

        // (3) Define tech list - Set up an array of tag technologies that your application wants to handle
        String[][] techList = new String[][]{};

        // Now we use all of the above
        adapter.enableForegroundDispatch(activity, mPendingIntent, mIntentFilters, techList);
    }

    /* ==================================================================================== */
    /* ==================================== Handling intents ============================== */
    /* ==================================================================================== */
    /* ==================================================================================== */

    @Override
    /**
     * Callback to process the data from the scanned NFC tag -
     * In our case this method gets called when the user attaches a Tag to the device.
     */
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    /* ==================================================================================== */

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            txtAction.setText(action);
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();

            // Find the tech
            String s = "";
            for (String tech : techList) {
                s += tech + "\n";
            }
            txtTech.setText(s);

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {
                new NdefReaderTask().execute(tag);
            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        }

        // ... Handle other types of intents
    }

    /* ==================================================================================== */
    /* ============================AsyncTask that reads the data ========================== */
    /* ==================================================================================== */

    /**
     * Background task for reading the data, so we do not block the UI thread while reading.
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            // Go over all of the records in our message
            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        /* ==================================================================================== */

        /* Read the message and return it as a string.
         * NFC forum specification includes more details: http://www.nfc-forum.org/specs/
         */
        private String readText(NdefRecord record) throws UnsupportedEncodingException {

            byte[] payload = record.getPayload();
            // Get the Text Encoding (bit_7 defines encoding)
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;
            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        /* ==================================================================================== */

        @Override
        // Update the data on the UI
        protected void onPostExecute(String result) {
            if (result != null) {
                txtNfcContent.setText(result);
            }
        }
    }
}