package com.example.weewilfred.freemg;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import static java.lang.Float.parseFloat;

import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignFisher;


/******** Signal Process Service reads the raw EMG data at 1000 samples a second, and processes it in chunks to deliver to the application in
 * various useful forms. Current services are: Envelope detection for evaluating activity over time, sliding heat  ***/
public class SignalProcessService extends Service {

    private static final String TAG = "DSPService";
    private final IBinder SignalProcessBinder = new MyLocalBinder();        //Object to bind the process to the service we will create the MyLocalBinder method
    EnvelopeDetector E =  new EnvelopeDetector(1000);                       //Create the envelope filter coefficients on first run
    int readCounter = 0;
    float[] emg = new float[5000];

    public SignalProcessService(){
        //Constructor to call this service from other classes
    }

    /********* When service is started, this process is executed that collects 5000 samples from the dummy data file and processes them ********/
    public float[] processEMG() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                //Give the service 5 seconds to parse a bunch of EMG data
                long futureTime = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < futureTime){
                    try{
                        toastHandler.sendEmptyMessage(0);
                        Log.i(TAG, "Signal Process Service is doing something");
                        wait(futureTime - System.currentTimeMillis());
                        String[] EMGdata = readFile("GeorgesLeftTrapTest.txt");
                         for (int i = 0; i< Array.getLength(EMGdata); i++){
                                //Convert the strings into floats for processing
                             emg[i] = parseFloat(EMGdata[i]);
                         }
                        emg = E.process(emg);  //Enveloped signal
                        //TODO: Output current emg intensity that can scale a hex color gradient
                        //TODO: Use envelope function to output relaxation out of 100
                        float[] tension = tensionModel(emg);

                    } catch(Exception e) {
                        Log.i(TAG, "Reading EMG data failed or signal processing");
                    }

                }
            }
        };
        Thread SignalProcessThread = new Thread();
        SignalProcessThread.start();
        return emg;
    }

    private final Handler toastHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            Toast.makeText(getApplicationContext(), "test", Toast.LENGTH_SHORT).show();
        }
    };



    /************************************* Service methods for creation, destruction, resuming and pausing etc ****************************/
    public class MyLocalBinder extends Binder {
        //Whenever you want to bind a client to a service, we need to make an object which extends the binder class
        //The only thing we want this class to be capable of is returning a reference to its superclass aka SignalProcessService
        //Then our old friend pieChart will be able to contact this class to get the details of this hot new service
        SignalProcessService getService(){
            return SignalProcessService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        //Handle what happens when this service is bound to a client,
        return SignalProcessBinder;

    }
    @Override
    public void onDestroy(){
        Log.i(TAG, "onDestroy method called");
    }

    /*********************************** DSP Processing functions and extra necessary functions below here *****************************/
    String[] readFile(String filename) throws IOException
    {
        List<String> mStrings = new ArrayList<>();

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader( new InputStreamReader( new FileInputStream(filename)));
            String str = reader.readLine();
            //Read 100 Dummy Data Entries
            while(str != null) {
                for (int i = 0 + readCounter * 5000; i < (readCounter * 5000 + 4999); i++) {
                    mStrings.add(str);
                    str = reader.readLine();
                }
            }
            Log.i(TAG, "Read the Dummy Data");
        } catch( IOException e ) {
            e.printStackTrace();
        }

        if(reader != null)
        {
            reader.close();
        }
        readCounter++;
        return mStrings.toArray(new String[mStrings.size()]);
    }

    public float[] tensionModel(float[] EMG){

        float Sp = (float) .01;
        float Ts = (float) .001;
        float Ti = (float) 20;
        int Kp = 1;
        float[] e = new float[5000];
        float[] I =  new float[5000];
        float Iprevious = 0;
        float Itotal = 0;

        for (int i = 0; i < Array.getLength(EMG); i++) {

            if (EMG[i] < 0){        //Rectify
                EMG[i] = -EMG[i];
            }


            if (EMG[i] > .03) {
                e[i] = Sp;
            } else {
                e[i] = EMG[i] - Sp;
            }
            I[i] = Iprevious + (Kp * e[i] * Ts / Ti);
            if (I[i] < 0)
                I[i] = 0;
            else if (I[i] > 0.15){
                I[i] = (float) 0.15;
            }
            Iprevious = I[i];
            Itotal += I[i];
        }
        float tension = .17 * 5000 /

        return I;
    }

    void membership(int x, float mu[]) {
        float c = (float) .17 / 8;  //Set the max tension level fuzzily
        float delta = c;
        float M[] = {c, 2 * c, 3 * c, 4 * c, 5 * c, 6 * c, 7 * c};

        for (int i = 0; i < 7; i++) {
            if (i == 0) {
                if (x <= M[i]) {
                    mu[i] = 1;
                } else if (x >= M[i] && x <= M[i] + delta) {
                    mu[i] = 1 - ((x - M[i]) / delta);
                } else {
                    mu[i] = 0;
                }
            }
            if (i > 0 && i < 6) {
                if (x >= (M[i] - delta) && x <= M[i]) {
                    mu[i] = (x - (M[i] - delta)) / delta;
                } else if (x >= M[i] && x <= M[i] + delta) {
                    mu[i] = 1 - ((x - M[i]) / delta);
                } else {
                    mu[i] = 0;
                }
            }
            if (i == 6) {
                if (x >= (M[i] - delta) && x <= M[i]) {
                    mu[i] = (x - (M[i] - delta)) / delta;
                } else if (x >= M[i]) {
                    mu[i] = 1;
                } else {
                    mu[i] = 0;
                }
            }
        }
    }





    /**
     * An envelope detector.
     * <p>The input signal is filtered by a bandpass filter before the envelope is detected.*/
    public class EnvelopeDetector {

        private IirFilter iirFilter;
        private double               gAttack;
        private double               gRelease;
        private double               level;

        /**
         * Constructs an envelope detector with default parameters for audio / speech.
         *
         * @param samplingRate
         *    Sampling rate in Hz.
         */
        public EnvelopeDetector (int samplingRate) {
            double attackTime = 0.0015;
            double releaseTime = 0.03;
            double lowerFilterCutoffFreq = 0.25;
            double upperFilterCutoffFreq = 1000;
            int filterOrder = 4;                                    // higher bandpass filter orders would be instable because of the small lower cutoff frequency
            double filterRipple = -0.5;
            double fcf1Rel = lowerFilterCutoffFreq / samplingRate;
            double fcf2Rel = upperFilterCutoffFreq / samplingRate;
            IirFilterCoefficients coeffs = IirFilterDesignFisher.design(FilterPassType.lowpass, FilterCharacteristicsType.chebyshev, filterOrder, filterRipple, fcf1Rel, fcf2Rel);
            IirFilter iirFilter = new IirFilter(coeffs);
            init(samplingRate, attackTime, releaseTime, iirFilter); }

        /**
         * Constructs an envelope detector.
         */

        private void init (int samplingRate, double attackTime, double releaseTime, IirFilter iirFilter) {
            gAttack  = Math.exp(-1 / (samplingRate * attackTime));
            gRelease = Math.exp(-1 / (samplingRate * releaseTime));
            this.iirFilter = iirFilter; }

        /**
         * Processes one input signal value and returns the current envelope level.
         */
        public double step (double inputValue) {
            double prefiltered = (iirFilter == null) ? inputValue : iirFilter.step(inputValue);
            double inLevel = Math.abs(prefiltered);
            double g = (inLevel > level) ? gAttack : gRelease;
            level = g * level + (1 - g) * inLevel;
            return level; }

        /**
         * Processes an array of input signal values and returns an array containing the envelope levels.
         */
        public float[] process (float[] in) {
            float[] out = new float[in.length];
            for (int i = 0; i < in.length; i++) {
                out[i] = (float)step(in[i]); }
            return out; }

    }
}
