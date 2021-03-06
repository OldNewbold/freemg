package com.example.weewilfred.freemg;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;

import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.widget.TextView;
import android.widget.Toast;

import com.example.weewilfred.freemg.SignalProcessService.MyLocalBinder;

import com.ftdi.j2xx.D2xxManager;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.github.mikephil.charting.formatter.PercentFormatter;


import static java.lang.Float.parseFloat;
import static java.lang.Thread.sleep;


public class PieChartFragment extends Fragment implements View.OnClickListener, OnChartValueSelectedListener {

    private static final String TAG = "PieChartFragment";

    //Service variables
    SignalProcessService pieService;
    boolean isBound = false;
    private static float[] emg = new float[4999];
    private static double[] forceDistribution = new double[3];
    public static D2xxManager ftD2xx = null;
    //Button Variables
    Button b;
    int commandIndex = 0;
    //Debug TextView Variables
    TextView e;
    //AlertDialog for naming test files Variables
    String YourEditTextValue;
    //PieChart Object
    private PieChart mChart;

    public PieChartFragment() {
        //Empty Constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        setRetainInstance(true);
        doBindService();
        View pieChartView = inflater.inflate(R.layout.pie_chart_fragment, container, false);

        mChart = (PieChart) pieChartView.findViewById(R.id.pieChart1);
        mChart.setDescription("");
        mChart.setDescriptionPosition(400, 100);

        Typeface tf = Typeface.DEFAULT;

        //Piechart text
        mChart.setCenterTextTypeface(tf);
        mChart.setCenterText(generateCenterText());
        mChart.setCenterTextSize(8f);

        //Set pieChart Click listener
        mChart.setOnChartValueSelectedListener(this);

        //Piechart centre circle
        mChart.setHoleColor(5000); //TODO: Change color of centre hole color to scale from blue to red as activity increases and decreases
        mChart.setHoleRadius(31.45f); //Radius of Pie Chart Center Hole in percent of maximum radius
        mChart.setTransparentCircleRadius(35f);
        mChart.setTransparentCircleColor(50);
        mChart.setBackgroundColor(1);
        Legend l = mChart.getLegend();
        l.setEnabled(false);
        //disable rotation
        mChart.setRotationEnabled(false);
        mChart.setData(generatePieData());

        //Set up for StartSensor Button
        b = (Button) pieChartView.findViewById(R.id.startButton);
        b.setOnClickListener(this);
        b.setTag(1);

        //Setup for standardised display pixels
        Resources r = getResources();
        final int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 350, r.getDisplayMetrics());

        //Set up for debug text
        e = (TextView) pieChartView.findViewById(R.id.debugText);
        e.setEnabled(true);
        e.setClickable(true);
        e.setTag(1);
        e.setText("No commands yet", TextView.BufferType.EDITABLE);
        /****************************************** A lot can happen when clicking *****************************************/
        e.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.debugText:
                        final int status2 = (Integer) view.getTag();
                        if (status2 == 1) {
                            e.setText("Connecting Sensor", TextView.BufferType.EDITABLE);
                            if (commandIndex == 0) {        //If this is the first click, set the f2td2xx context
                                try {
                                    ftD2xx = D2xxManager.getInstance(getContext());
                                } catch (D2xxManager.D2xxException ex) {
                                    ex.printStackTrace();
                                }
                                commandIndex++;
                            }
                            //Build an alert dialog to get the session name
                            AlertDialog.Builder alert = new AlertDialog.Builder(getContext(), R.style.AppTheme);

                            final EditText edittext = new EditText(getContext());
                            alert.setMessage("New Session");
                            alert.setTitle("Enter a name for your test!");
                            alert.setView(edittext);

                            alert.setPositiveButton("Start Muscular Test", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    YourEditTextValue = edittext.getText().toString();
                                    //Configure the sensor and begin the acquisition
                                    pieService.notifyUSBDeviceAttach(getContext(), ftD2xx);
                                    //Create a new file for the EMG traces
                                    File F = (pieService.generateFileOnSD(YourEditTextValue));
                                    //Set the current EMG trace as the created file
                                    pieService.setCurrentEMGTrace(F);
                                }
                            });

                            alert.setNegativeButton("I'm not ready", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    pieService.notifyUSBDeviceDetach();
                                }
                            });

                            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                            AlertDialog alertDialog = alert.create();
                            lp.copyFrom(alertDialog.getWindow().getAttributes());
                            lp.width = px;
                            lp.height = px;
                            lp.x = 0;
                            lp.y = -100;
                            alertDialog.getWindow().setAttributes(lp);
                            //Show the dialog once all the values are built
                            alertDialog.show();

                            view.setTag(0);
                            e.setText("Click again to disconnect", TextView.BufferType.EDITABLE);
                        } else {
                            e.setText("RS232 response: " + pieService.readDataToText);
                            pieService.notifyUSBDeviceDetach();
                            commandIndex--;
                            view.setTag(1);
                        }
                }
            }
        });
        /*************************************** The clicking is over, return the completed view *******************************/
        return pieChartView;
    }

    @Override
    public void onResume() {
        super.onResume();
        mChart.animateY(1200, Easing.EasingOption.EaseInOutQuad);
        mChart.spin(1200, 180, 360, Easing.EasingOption.EaseInOutQuad);
    }


    private SpannableString generateCenterText() {
        SpannableString s = new SpannableString("EMG Spot");
        s.setSpan(new RelativeSizeSpan(2f), 0, 8, 0);
        s.setSpan(new ForegroundColorSpan(Color.BLACK), 8, s.length(), 0);
        return s;
    }

    /*******
     * Function used to check if the signal Service process has completed without holding up the UI thread
     *****/
    public void pollService() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                while (pieService.sensorFinish != true) {
                }
                //do other things
            }
        };
        Thread pollService = new Thread(r);
        pollService.start();
    }

    /*******************
     * use this function to receive data from the sensorProcessService *
     **************************************/
    public void getSensorData() {

        pieService.processEMG();
        forceDistribution = pieService.getForceDistribution();
        emg = pieService.getEmg();
        //tension = pieService.getNormalizedTension();
    }

    /******************
     * Handle Clicks
     *
     * @param v - the view object that was clicked
     ****************************/
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startButton:
                final int status = (Integer) v.getTag();
                if (status == 1) {
                    getSensorData();
                    emgColor();
                    b.setText("Stop Sensor", TextView.BufferType.EDITABLE);
                    v.setTag(0);
                    Log.d(TAG, "getSensorData has been called to run");
                    mChart.setData(generatePieData());
                    mChart.invalidate();
                    mChart.refreshDrawableState();
                } else {
                    b.setText("Start Sensor", TextView.BufferType.EDITABLE);
                    v.setTag(1); //pause
                }
                break;
        }
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {

        if (e == null)
            return;
        Log.i("VAL SELECTED",
                "Value: " + e.getY() + ", index: " + h.getX()
                        + ", DataSet index: " + h.getDataSetIndex());
    }

    @Override
    public void onNothingSelected() {

    }


    //TODO: Data from EMG sensor is sent to this function for updating UI and animating
    protected PieData generatePieData() {
        Typeface tf;
        tf = Typeface.MONOSPACE;
        int elapsedTime;

        ArrayList<PieEntry> entries1 = new ArrayList<PieEntry>();
        if (isBound == true) {
            entries1.add(new PieEntry((float) forceDistribution[0], "Low"));
            entries1.add(new PieEntry((float) forceDistribution[1], "Medium"));
            entries1.add(new PieEntry((float) forceDistribution[2], "High"));
            //Log.d(TAG, "Medium: " + forceDistribution[1] + " Low: " + forceDistribution[0] + " High: " + forceDistribution[2]);
        } else {
            entries1.add(new PieEntry(33, "Low"));
            entries1.add(new PieEntry(33, "Medium"));
            entries1.add(new PieEntry(33, "High"));
        }


        PieDataSet ds1 = new PieDataSet(entries1, "Spotty");
        if (isBound == true) {
            elapsedTime = (pieService.getReadCounter() * 5);
            mChart.setCenterText("Elapsed Time " + Integer.toString(elapsedTime));
        }

        int[] relaxometer = getContext().getResources().getIntArray(R.array.relaxometer);
        ds1.setColors(ColorTemplate.createColors(relaxometer));

        ds1.setSliceSpace(1f);
        ds1.setValueTextColor(Color.WHITE);
        ds1.setValueTextSize(12f);
        PieData d = new PieData(ds1);
        d.setValueTypeface(tf);

        return d;
    }

    protected void emgColor() {
        emg = pieService.getEmg();
        Log.d(TAG, "emgColor thread running");
        /*ObjectAnimator colorFade = ObjectAnimator.ofObject(mChart.getAnimation(), "HoleColor", new ArgbEvaluator(), Color.argb(255,255,255,255), 0xff000000);
        colorFade.setDuration(5000);
        colorFade.start();*/
        /*Runnable r = new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i > 245; i++) {
                    mChart.setHoleColor(Color.rgb(i, i, i)); //TODO: Change color of centre hole color to scale from blue to red as activity increases and decreases
                    mChart.refreshDrawableState();
                    try{ sleep(19); } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            };
        Thread SignalProcessThread = new Thread(r);
        SignalProcessThread.start();*/

    }

    private ServiceConnection pieConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            SignalProcessService.MyLocalBinder Binder = (SignalProcessService.MyLocalBinder) service;
            pieService = Binder.getService();
            isBound = true;
            Log.d(TAG, "Service is bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
        }
    };

    void doBindService() {
        //Bind service to application then start the service
        Intent i = new Intent(getActivity().getApplicationContext(), SignalProcessService.class);
        getActivity().getApplicationContext().bindService(i, pieConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Attempted service bind");
    }

    void doUnbindService() {
        if (isBound) {
            // Detach our existing connection.
            getActivity().getApplicationContext().unbindService(pieConnection);
            isBound = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    /*D2XX function might need this*/
    /***@Override
     protected void onNewIntent(Intent intent)
     {
     String action = intent.getAction();
     if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
     {
     switch (currect_index)
     {
     case 4:
     ((OpenDeviceFragment)currentFragment).notifyUSBDeviceAttach(intent);
     break;
     case 5:
     ((DeviceUARTFragment)currentFragment).notifyUSBDeviceAttach();
     break;
     case 7:
     ((EEPROMFragment)currentFragment).notifyUSBDeviceAttach();
     break;
     case 8:
     ((EEPROMUserAreaFragment)currentFragment).notifyUSBDeviceAttach();
     break;
     default:
     break;
     }
     }
     }*/

}
