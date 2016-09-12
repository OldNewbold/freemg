package com.example.weewilfred.freemg;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import java.util.ArrayList;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.widget.TextView;

import com.example.weewilfred.freemg.SignalProcessService.MyLocalBinder;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;



public class PieChartFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "PieChartFragment";
    SignalProcessService pieService;
    boolean isBound = false;
    private static float[] emg = new float[5000];
    static float tension;
    Button b;
    private PieChart mChart;


    public PieChartFragment() {
    }




    @Override
    public View onCreateView(LayoutInflater inflater,@Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View pieChartView = inflater.inflate(R.layout.pie_chart_fragment, container, false);

        mChart = (PieChart) pieChartView.findViewById(R.id.pieChart1);
        mChart.setDescription("Brain Training Tools");
        mChart.setDescriptionPosition(400,100);

        Typeface tf = Typeface.DEFAULT;

        //Piechart text
        mChart.setCenterTextTypeface(tf);
        mChart.setCenterText(generateCenterText());
        mChart.setCenterTextSize(8f);

        //Piechart centre circle
        mChart.setHoleColor(5000); //TODO: Change color of centre hole color to scale from blue to red as activity increases and decreases
        mChart.setHoleRadius(30f); //Radius of Pie Chart Center Hole in percent of maximum radius
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

        doBindService();
        return pieChartView;
    }

    @Override
    public void onResume() {
        super.onResume();
        mChart.animateY(1500, Easing.EasingOption.EaseInOutQuad);
        mChart.spin(1500, 0, 360, Easing.EasingOption.EaseInOutQuad);
    }


    private SpannableString generateCenterText() {
        SpannableString s = new SpannableString("Current Activity");
        s.setSpan(new RelativeSizeSpan(2f), 0, 16, 0);
        s.setSpan(new ForegroundColorSpan(Color.BLACK), 8, s.length(),0);
        return s;
    }

    public void getSensorData(){
        //TODO: use this function to receive data from the sensor and send it to generate pie data
        emg = pieService.processEMG();
        tension = pieService.integrateFunction();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startButton:
                final int status =(Integer) v.getTag();
                if(status == 1) {
                    getSensorData();
                    mChart.setData(generatePieData());
                    mChart.invalidate();
                    mChart.refreshDrawableState();
                    b.setText("Stop Sensor", TextView.BufferType.EDITABLE);
                    v.setTag(0); //pause
                } else {
                    b.setText("Start Sensor", TextView.BufferType.EDITABLE);
                    v.setTag(1); //pause
                }
                Log.d(TAG, "getSensorData has been called to run");
                break;
        }
    }

    //TODO: Data from EMG sensor is sent to this function for processing and animating
    protected PieData generatePieData() {
        Typeface tf;
        tf = Typeface.DEFAULT;
        //int count = 1;

        ArrayList<PieEntry> entries1 = new ArrayList<PieEntry>();
        //getSensorData();
        //Populate the arraylist either in a loop or individually
        entries1.add(new PieEntry((100-tension), "Relaxation "));
        entries1.add(new PieEntry((float) ((Math.random() * 60)+ 40), "Tension "));


        PieDataSet ds1 = new PieDataSet(entries1, "Spotty");
        int[] relaxometer = getContext().getResources().getIntArray(R.array.relaxometer);
        ds1.setColors(ColorTemplate.createColors(relaxometer));
        ds1.setSliceSpace(1f);
        ds1.setValueTextColor(Color.WHITE);
        ds1.setValueTextSize(12f);

        PieData d = new PieData(ds1);
        d.setValueTypeface(tf);

        return d;
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
        getActivity().getApplicationContext().bindService(i, pieConnection, Context.BIND_AUTO_CREATE );
        Log.d(TAG, "Attempted service bind" );
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

}
