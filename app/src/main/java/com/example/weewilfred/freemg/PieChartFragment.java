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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.view.MotionEvent;

import java.util.ArrayList;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;



public class PieChartFragment extends Fragment {

    public PieChartFragment() {
    }

    private RelativeLayout pieChartLayout;
    private OnFragmentInteractionListener mListener;
    private PieChart mChart;


    @Override   //onCreateView is where we tell the class what design or XML we will be using
    public View onCreateView(LayoutInflater inflater,@Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {

        View pieChartView = inflater.inflate(R.layout.pie_chart_fragment, container, false);

        mChart = (PieChart) pieChartView.findViewById(R.id.pieChart1);
        mChart.setDescription("Relaxometer");

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
        //disable rotation
        mChart.setRotationEnabled(false);

        mChart.setData(generatePieData());

        return pieChartView;
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }

    private SpannableString generateCenterText() {
        SpannableString s = new SpannableString("Current Activity");
        s.setSpan(new RelativeSizeSpan(2f), 0, 16, 0);
        s.setSpan(new ForegroundColorSpan(Color.BLACK), 8, s.length(),0);
        return s;
    }
    int getSensorData(){

        //TODO: use this function to receive data from the sensor and send it to generate pie data

        return (0);
    }

    //TODO: Data from EMG sensor is sent to this function for processing and animating
    protected PieData generatePieData() {
        Typeface tf;
        tf = Typeface.DEFAULT;
        //int count = 1;

        ArrayList<PieEntry> entries1 = new ArrayList<PieEntry>();

        //Populate the arraylist either in a loop or individually
        entries1.add(new PieEntry((float) ((Math.random() * 60) + 40), "Relaxation "));
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
}
