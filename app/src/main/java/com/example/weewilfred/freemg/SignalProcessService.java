package com.example.weewilfred.freemg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import static java.lang.Float.parseFloat;


/********
 * Signal Process Service reads the raw EMG data at 1000 samples a second, and processes it in chunks to deliver to the application in
 * various useful forms. Current services are: Envelope detection for evaluating activity over time, sliding heat
 ***/
public class SignalProcessService extends Service {

    //DSPservice global variables
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

    //Dt2xx Global Variables
    static int iEnableReadFlag = 1;
    byte addSensor2[] = {0x52, 0x00, 0x02, 0x01, 0x02, (byte) 0xB7, 0x5E, 0x00, 0x05, 0x00, 0x55, (byte)0x94, 0x02, 0x01, 0x00, 0x00};
    byte getSensor2[] = {0x52, 0x00, 0x04, 0x01};
    byte setAcqusition[] = {0x52, 0x00, 0x05, 0x03, 0x02};      //byte 5: 0x00 = raw, 0x01 = ADPCM, 0x02 = Envelope
    byte setTrigger[] = {0x52, 0x00, 0x08, 0x01};
    byte startSensor[] = {0x52, 0x02, 0x09};
    byte pad = 0x00;
    byte errorCheckPad[] = {0x02, 0x01};
    byte[] sensorCommands[] = {addSensor2, getSensor2, setAcqusition, setTrigger, startSensor};
    byte readText[];

    static Context DeviceUARTContext;
    D2xxManager ftdid2xx;
    FT_Device ftDev = null;
    int DevCount = -1;
    int currentIndex = -1;
    int openIndex = 0;

    /* D2xx local variables*/
    int baudRate = 230400; /*baud rate*/
    byte stopBit = 1; /*1:1stop bits, 2:2 stop bits*/
    byte dataBit = 8; /*8:8bit, 7: 7bit*/
    byte parity = 0;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
    byte flowControl = 1; /*0:none, 1: flow control(CTS,RTS)*/
    //int portNumber; /*port number*/
    //ArrayList<CharSequence> portNumberList;


    public static final int readLength = 512;
    public int readcount = 0;
    public int iavailable = 0;
    byte[] readData = new byte[readLength];
    char[] readDataToText = new char[readLength];
    public boolean bReadThreadGoing = false;
    public readThread read_thread;

    boolean uart_configured = false;

    /* Empty Constructor */
    public SignalProcessService(){
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


    /*******************************
     *  Getters and Setters
     ***********************/
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
    public byte[] getSensorCommands(int i){
        return sensorCommands[i];
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
        //Handle what happens when this service is bound to a client

        return SignalProcessBinder;
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy method but only this log runs");
    }

    /**********************     FTD2XX Functions    ****************************************************/


    public void notifyUSBDeviceAttach(Context parentContext , D2xxManager ftdid2xxContext)
    {
        DeviceUARTContext = parentContext;
        ftdid2xx = ftdid2xxContext;

        DevCount = 0;
        createDeviceList();
        if(DevCount > 0)
        {
            try {
                connectFunction();
                SetConfig(baudRate, dataBit, stopBit, parity, flowControl);
                EnableRead();
                /****Procedure for connecting sensor 2 sends all of the byteCommands to the sendMessage function ***/
                //for (int i = 0; i < Array.getLength(sensorCommands); i++){
                Toast.makeText(DeviceUARTContext,"Command: " + sensorCommands[0] ,Toast.LENGTH_SHORT).show();
                SendMessage(sensorCommands[0]);
                /*while (readData != sensorSuccess[0]) {
                    //try again or offer suggestions
                }*/
                SendMessage(sensorCommands[1]);
                SendMessage(sensorCommands[2]);
                SendMessage(sensorCommands[3]);
                SendMessage(sensorCommands[4]);
                //}
            }catch (Exception e){
                Toast.makeText(DeviceUARTContext,"connection failure to sensor, you dingus",Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void notifyUSBDeviceDetach()
    {
        disconnectFunction();
    }

    public void createDeviceList()
    {
        int tempDevCount = ftdid2xx.createDeviceInfoList(DeviceUARTContext);
        Toast.makeText(DeviceUARTContext,"Devices found: " + tempDevCount ,Toast.LENGTH_SHORT).show();
        if (tempDevCount > 0)
        {
            if( DevCount != tempDevCount )
            {
                DevCount = tempDevCount;
                //updatePortNumberSelector();
            }
        }
        else
        {
            DevCount = -1;
            currentIndex = -1;
        }
    }
    public void disconnectFunction()
    {
        DevCount = -1;
        currentIndex = -1;
        bReadThreadGoing = false;
        try {
            Thread.sleep(50);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(ftDev != null)
        {
            synchronized(ftDev)
            {
                if( true == ftDev.isOpen())
                {
                    ftDev.close();
                }
            }
        }
    }

    public void connectFunction()
    {
        int tmpProtNumber = openIndex + 1;

        if( currentIndex != openIndex )
        {
            if(null == ftDev)
            {
                ftDev = ftdid2xx.openByIndex(DeviceUARTContext, openIndex);
            }
            else
            {
                synchronized(ftDev)
                {
                    Toast.makeText(DeviceUARTContext,"Attempting to open port",Toast.LENGTH_SHORT).show();
                    ftDev = ftdid2xx.openByIndex(DeviceUARTContext, openIndex);
                }
            }
            uart_configured = false;
        }
        else
        {
            Toast.makeText(DeviceUARTContext,"Device port " + tmpProtNumber + " is already opened",Toast.LENGTH_LONG).show();
            return;
        }

        if(ftDev == null)
        {
            Toast.makeText(DeviceUARTContext,"open device port("+tmpProtNumber+") NG, ftDev == null", Toast.LENGTH_LONG).show();
            return;
        }

        if (true == ftDev.isOpen())
        {
            currentIndex = openIndex;
            Toast.makeText(DeviceUARTContext, "open device port(" + tmpProtNumber + ") OK", Toast.LENGTH_SHORT).show();

            if(false == bReadThreadGoing)
            {
                read_thread = new readThread(handler);
                read_thread.start();
                bReadThreadGoing = true;
                Toast.makeText(DeviceUARTContext, "Reading USB Responses", Toast.LENGTH_SHORT).show();

            }
        }
        else
        {
            Toast.makeText(DeviceUARTContext, "open device port(" + tmpProtNumber + ") NG", Toast.LENGTH_LONG).show();
            //Toast.makeText(DeviceUARTContext, "Need to get permission!", Toast.LENGTH_SHORT).show();
        }
    }
    public void SetConfig(int baud, byte dataBits, byte stopBits, byte parity, byte flowControl)
    {
        if (ftDev.isOpen() == false) {
            //Log.e("j2xx", "SetConfig: device not open");
            Toast.makeText(DeviceUARTContext, "j2xx, Device not open", Toast.LENGTH_SHORT).show();
            return;
        }

        // configure our port
        // reset to UART mode for 232 devices
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);

        ftDev.setBaudRate(baud);

        switch (dataBits) {
            case 7:
                dataBits = D2xxManager.FT_DATA_BITS_7;
                break;
            case 8:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
            default:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
        }

        switch (stopBits) {
            case 1:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
            case 2:
                stopBits = D2xxManager.FT_STOP_BITS_2;
                break;
            default:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
        }

        switch (parity) {
            case 0:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
            case 1:
                parity = D2xxManager.FT_PARITY_ODD;
                break;
            case 2:
                parity = D2xxManager.FT_PARITY_EVEN;
                break;
            case 3:
                parity = D2xxManager.FT_PARITY_MARK;
                break;
            case 4:
                parity = D2xxManager.FT_PARITY_SPACE;
                break;
            default:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
        }

        ftDev.setDataCharacteristics(dataBits, stopBits, parity);

        short flowCtrlSetting;
        switch (flowControl) {
            case 0:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
            case 1:
                flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
                break;
            case 2:
                flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
                break;
            case 3:
                flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
                break;
            default:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
        }

        // TODO : flow ctrl: XOFF/XOM
        // TODO : flow ctrl: XOFF/XOM
        ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);

        uart_configured = true;
        Toast.makeText(DeviceUARTContext, "Config done", Toast.LENGTH_SHORT).show();
    }
    public void SendMessage(final byte[] byteCode) {
        Runnable R = new Runnable() {
            @Override
            public void run() {
                if (ftDev.isOpen() == false) {
                    Log.e("j2xx", "SendMessage: device not open");
                    Toast.makeText(DeviceUARTContext, "SendMessage: device not open", Toast.LENGTH_SHORT).show();
                    return;
                }
                ftDev.setLatencyTimer((byte) 16);
                ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                byte[] OutData = byteCode;      //byteCode is the current request code in a byte array

                if (Array.getLength(byteCode) != 16) {
                    for (int i = Array.getLength(byteCode); i < 16; i++) {
                        if (i < 14) {
                            OutData[i] = pad;
                            //ftDev.write(pad, Array.getLength(pad));
                        }
                        else{
                            //ftDev.write(errorCheckPad, Array.getLength(errorCheckPad));
                            OutData[i] = errorCheckPad[i-15];
                        }
                    }
                }
                Toast.makeText(DeviceUARTContext, "Length of Command" + Array.getLength(OutData), Toast.LENGTH_SHORT).show();
                ftDev.write(OutData, Array.getLength(OutData));
            }
        };
        Thread sendmsg = new Thread(R);
        sendmsg.run();

    }
    public void EnableRead (){
        iEnableReadFlag = (iEnableReadFlag + 1)%2;

        //if(iEnableReadFlag == 1) {
            ftDev.purge((byte) (D2xxManager.FT_PURGE_TX));
            ftDev.restartInTask();
            //readEnButton.setText("Read Enabled");
            Toast.makeText(DeviceUARTContext, "Read Enabled", Toast.LENGTH_SHORT).show();
        /*}
        else{
            ftDev.stopInTask();
            //readEnButton.setText("Read Disabled");
            Toast.makeText(DeviceUARTContext, "Read Disabled", Toast.LENGTH_SHORT).show();
        }*/
    }
    final Handler handler =  new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            if(iavailable > 0)
            {
                readText.equals(String.copyValueOf(readDataToText, 0, iavailable));
            }
        }
    };

    private class readThread  extends Thread
    {
        Handler mHandler;

        readThread(Handler h){
            mHandler = h;
            this.setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run()
        {
            int i;

            while(true == bReadThreadGoing)
            {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {

                }

                synchronized(ftDev)
                {
                    iavailable = ftDev.getQueueStatus();
                    if (iavailable > 0) {

                        if(iavailable > readLength){
                            iavailable = readLength;
                        }
                        ftDev.read(readData, iavailable);
                        for (i = 0; i < iavailable; i++) {
                            readDataToText[i] = (char) readData[i];
                        }
                        Message msg = mHandler.obtainMessage();
                        mHandler.sendMessage(msg);
                    }
                }
            }
        }
    }

    /**
     * Hot plug for plug in solution
     * This is workaround before android 4.2 . Because BroadcastReceiver can not
     * receive ACTION_USB_DEVICE_ATTACHED broadcast
     */

}
