package com.example.weewilfred.freemg;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Float.parseFloat;


/********
 * Signal Process Service reads the raw EMG data at 1000 samples a second, and processes it in chunks to deliver to the application in
 * various useful forms. Current services are: Envelope detection for evaluating activity over time, sliding heat
 ***/
public class SignalProcessService extends Service {

    private static final String TAG = "DSPService";
    private final IBinder SignalProcessBinder = new MyLocalBinder();        //Object to bind the process to the service we will create the MyLocalBinder method
    int readCounter = 0;
    static double[] h = {0.020132210722515607, 0.014337588088026874, -0.06042518986827016, -0.11688176581198412, 0.015390548525687591, 0.30600043556088063, 0.464289723357815, 0.30600043556088063, 0.015390548525687591, -0.11688176581198412, -0.06042518986827016, 0.014337588088026891, 0.020132210722515607};
    private static double[] distribution = new double[3];
    static float[] emg = new float[5000];
    static float normalizedTension = 0;
    static double[] forceDistribution =  new double[3];
    int getTime = 5;                                                        //How many seconds of data should we process at a time?
    float Iarea = 0;
    boolean sensorFinish = false;

    public SignalProcessService() {
        //Constructor to call this service from other classes
    }

    /*********
     * When service is started, this process is executed that collects 5000 samples from the dummy data file and processes the normalized tension and emg
     ********/
    public void processEMG() {
        Log.i(TAG, "Signal Process Service is doing something");
        sensorFinish = false;       //Flag to let pieFragment know when processing is finished.
        Runnable r = new Runnable() {
            @Override
            public void run() {
                //Give the service 5 seconds to parse a bunch of EMG data
                try {
                    String[] EMGdata = readFile();
                    for (int i = 0; i < Array.getLength(EMGdata); i++) {  //grab 5 seconds worth of data (5000 samples)
                        emg[i] = parseFloat(EMGdata[i]);                   //Convert the strings into floats for processing
                    }
                    emg = EnvelopeDetector(emg);  //Envelope signal with LPF

                } catch (Exception e) {
                    Log.i(TAG, "Process EMG");
                }
                forceDistribution = amplitudeDistribution(.0075, .015);
                //integrateFunction();
            }
        };

        Thread SignalProcessThread = new Thread(r);
        SignalProcessThread.start();
    }
    /* Gathers 5 seconds of emg data and distributes it into low, medium and high values. After 5 minutes, the distribution value is saved and the process restarts*/
    public double[] amplitudeDistribution(double lowthresh, double highthresh){

        double low = 0, medium = 0, high = 0;


        for (int i = 0; i < Array.getLength(emg); i++){
                if (emg[i] >= lowthresh && emg[i] <= highthresh){
                    medium++;           //Each sample detected = 1/1000th of a second spent in the detected quadrant
                }
                else if ( emg[i] >= highthresh){
                    high++;
                }
                else {
                    low++;
                }
        }
        /*Weight the quadrants accordingly and calculate the time in each; since we take every 5th sample, multiply by time factor of .005 for seconds # of seconds
        if we haven't reached 5 minutes, keep summing the number of seconds spent*/
        if (readCounter > 0 && readCounter % 60 != 0) {
            distribution[0] = distribution[0] +  low * .001;
            distribution[1] = distribution[1] + medium * .001;
            distribution[2] = distribution[2] + high * .001;         // As 1 is the weighting of high
        }
        else {
            distribution[0] = low * .001;         //1/3*.005 = .001666
            distribution[1] = medium * .001;
            distribution[2] = high * .001;          // As 1 is the weighting of high
        }
        return (distribution);
    }

    public void integrateFunction() {

        float Sp = (float) .015, Ts = (float) .001, Ti = (float) 20, Iprevious = 0; //Integrative constants
        float[] e = new float[5000], I = new float[5000];                           // Arrays for storing values
        int Kp = 1;

        for (int i = 0; i < Array.getLength(emg); i++) {
            if (emg[i] > .03) {
                e[i] = Sp;
            } else {
                e[i] = emg[i] - Sp;
            }
            I[i] = Iprevious + (Kp * e[i] * Ts / Ti);
            if (I[i] < 0) I[i] = 0;
            else if (I[i] > 0.15) {
                I[i] = (float) 0.15;
            }
            Iprevious = I[i];
            Iarea += I[i];
        }
        //Calculate tension based on results of integration compared with a standard metric set by calibration
        //tensionArea = 0.015*5000 samples
        normalizedTension = (float) ((Iarea/.15)*100);
        sensorFinish = true;                                             //Flag to let pieFragment know when processing is finished.
        Log.d(TAG, "Iarea: " + Iarea + " NormalizedTension: " + normalizedTension);
    }

    public float getNormalizedTension(){
        return Math.abs(normalizedTension);
    }
    public float[] getEmg(){
        return emg;
    }
    public float getIarea(){
        return Iarea;
    }
    public double[] getForceDistribution(){
        return forceDistribution;
    }
    public int getReadCounter(){
        return readCounter;
    }


    /***********************************
     * DSP Processing functions and extra necessary functions below here
     *****************************/

  /* Processes the Emg signal with a LPF, FC of 200Hz */
    public float[] EnvelopeDetector(float[] signal) {
        //filter coefficients reside in variable h
        int Nsignal = Array.getLength(signal);
        int Nh = Array.getLength(h);
        //Rectification
        for (int i = 0; i < Nsignal; i++) {
            signal[i] = Math.abs(signal[i]);
        }
        //Linear convolution with LPF fc = 200Hz, G = 1, Fs = 1000;
        for (int i = 0; i < Nsignal; i++) {
            for (int j = 0; j < Nh; j++) {
                if ((i + 1) - j > 0 && ((i + 1) - j) < Nsignal) {
                    //We extract the rms signal value for the envelope
                    signal[i] = (float) ((signal[i] + h[j] * signal[(i + 1) - j]) / Math.sqrt(2));

            }
            }
        }
        return signal;
    }

    /*****************
     * File reading function to read the EMG dummy data
     *************************/
    String[] readFile() throws IOException {
        List<String> mStrings = new ArrayList<>();

        BufferedReader reader = null;
        try {
            InputStream is = this.getResources().openRawResource(R.raw.lefttrap);
            reader = new BufferedReader(new InputStreamReader(is));
            reader.skip(readCounter*getTime*1000);
            String str = reader.readLine();
            while (str != null) {
                for (int i = (readCounter * getTime * 1000); i < ((readCounter*getTime*1000) + getTime * 1000); i++) {
                    mStrings.add(str);
                    str = reader.readLine();
                }
            }
            Log.i(TAG, "Read the Dummy Data " + "i begins at: " + (readCounter* getTime* 1000) + " and ends at " + ((readCounter*getTime*1000) + getTime * 1000));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (reader != null) {
            reader.close();
        }
        readCounter++;
        Log.d(TAG, "The read count is " + readCounter);
        return mStrings.toArray(new String[mStrings.size()]);
    }

    /*************************************
     * Service methods for creation, destruction, resuming and pausing etc
     ****************************/


    public class MyLocalBinder extends Binder {
        //Whenever you want to bind a client to a service, we need to make an object which extends the binder class
        //The only thing we want this class to be capable of is returning a reference to its superclass aka SignalProcessService
        //Then our old friend pieChart will be able to contact this class to get the details of this hot new service
        SignalProcessService getService() {
            return SignalProcessService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        //Handle what happens when this service is bound to a client,
        return SignalProcessBinder;
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy method but only this log runs");
    }


}
