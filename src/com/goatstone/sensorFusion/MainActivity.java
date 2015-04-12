/************************************************************************************
 * Copyright (c) 2014 Jose Collas
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 ************************************************************************************/
package com.goatstone.sensorFusion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Display;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.goatstone.util.SensorFusion;

public class MainActivity extends Activity
        implements SensorEventListener, RadioGroup.OnCheckedChangeListener {

	//2015-4-12 14:46:40£¨ zhangxaochen£∫
	final double NS2S = 1./1e9;
	float[] _rotVec = { 0, 0, 0 };
	float[] _oriFromRotVec = { 0, 0, 0 };
	Writer _writer;
	boolean _doWriteLogFile = false;
	
    private SensorFusion sensorFusion;
    private BubbleLevelCompass bubbleLevelCompass;
    private SensorManager sensorManager = null;

    private RadioGroup setModeRadioGroup;
    private TextView azimuthText, pithText, rollText;
    private DecimalFormat d = new DecimalFormat("#.##");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        registerSensorManagerListeners();

        d.setMaximumFractionDigits(2);
        d.setMinimumFractionDigits(2);

        sensorFusion = new SensorFusion();
        sensorFusion.setMode(SensorFusion.Mode.ACC_MAG);

        bubbleLevelCompass = (BubbleLevelCompass) this.findViewById(R.id.SensorFusionView);
        setModeRadioGroup = (RadioGroup) findViewById(R.id.radioGroup1);
        azimuthText = (TextView) findViewById(R.id.azmuth);
        pithText = (TextView) findViewById(R.id.pitch);
        rollText = (TextView) findViewById(R.id.roll);
        setModeRadioGroup.setOnCheckedChangeListener(this);
        
		
    }

    public void updateOrientationDisplay() {

        double azimuthValue = sensorFusion.getAzimuth();
        double rollValue =  sensorFusion.getRoll();
        double pitchValue =  sensorFusion.getPitch();

        azimuthText.setText(String.valueOf(d.format(azimuthValue)));
        pithText.setText(String.valueOf(d.format(pitchValue)));
        rollText.setText(String.valueOf(d.format(rollValue)));

        bubbleLevelCompass.setPLeft((int) rollValue);
        bubbleLevelCompass.setPTop((int) pitchValue);
        bubbleLevelCompass.setAzimuth((int) azimuthValue);

    }

    public void registerSensorManagerListeners() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
        
        //2015-4-12 14:43:29£¨ zhangxaochen£∫ “‘º∞ rotation_vector
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
				SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerSensorManagerListeners();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sensorFusion.setAccel(event.values);
                sensorFusion.calculateAccMagOrientation();
                break;

            case Sensor.TYPE_GYROSCOPE:
                sensorFusion.gyroFunction(event);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                sensorFusion.setMagnet(event.values);
                break;
                
                //2015-4-12 14:44:58, zhangxaochen:
            case Sensor.TYPE_ROTATION_VECTOR:
            	_rotVec = event.values.clone();
            	float[] rmat = new float[9];
            	SensorManager.getRotationMatrixFromVector(rmat, _rotVec);
            	SensorManager.getOrientation(rmat, _oriFromRotVec);
//            	SensorManager.getQuaternionFromVector(Q, rv)
            	break;
        }
        updateOrientationDisplay();
        
        double ts = event.timestamp * NS2S;
        float[] a = sensorFusion.getAccMagOrientation(),
        		b = sensorFusion.getGyroOrientation(),
        		c = sensorFusion.getFusedOrientation();
        
		if (_doWriteLogFile) {
			try {
				_writer.write("" + ts + ", " + _oriFromRotVec[0] + ", "
						+ _oriFromRotVec[1] + ", " + _oriFromRotVec[2] + ", "
						+ a[0] + ", " + a[1] + ", " + a[2] + ", " + b[0] + ", "
						+ b[1] + ", " + b[2] + ", " + c[0] + ", " + c[1] + ", "
						+ c[2] + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }//onSensorChanged

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

        switch (checkedId) {
            case R.id.radio0:
                sensorFusion.setMode(SensorFusion.Mode.ACC_MAG);
                break;
            case R.id.radio1:
                sensorFusion.setMode(SensorFusion.Mode.GYRO);
                break;
            case R.id.radio2:
                sensorFusion.setMode(SensorFusion.Mode.FUSION);
                break;
        }
    }

    public void onBubbleLevelClicked(View v) {
		System.out.println("onBubbleLevelClicked()");
		//TODO
		_doWriteLogFile = !_doWriteLogFile;
		if (_doWriteLogFile) {
			try {
				// 2015-4-12 15:11:18
				// Date date = new Date();
				String tsString = new SimpleDateFormat("yyyyMMdd'T'HHmmss")
						.format(new Date());
				String logFname = "androidSFzc-" + tsString + ".txt";
				File extDir = Environment.getExternalStorageDirectory();
				File fout = new File(extDir, logFname);
				FileOutputStream foutStream = new FileOutputStream(fout);
				// FileOutputStream foutStream = openFileOutput(logFname,
				// Context.MODE_WORLD_WRITEABLE);
				_writer = new BufferedWriter(new OutputStreamWriter(foutStream,
						"utf-8"));

				Toast.makeText(this, fout.getAbsolutePath(), Toast.LENGTH_SHORT)
						.show();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} 
		else { //!_doWriteLogFile
	        try {
				_writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Toast.makeText(this, "FINISHED writing", Toast.LENGTH_SHORT).show();
		}
		
	}
}