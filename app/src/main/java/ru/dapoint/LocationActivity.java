package ru.dapoint;

import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LocationActivity extends Activity {

	String loginName;
	TextView tvHello;
	TextView tvLog;
	TextView tvState;
	SharedPreferences sPref;
	Button daButton;
	Button changeButton;
	EditText settingsEditTextRouterIP;
	EditText settingsEditTextBeaconFreq;
	Button settingsButtonSaveSettings;
	Button settingsButtonSettingsResetFields;

	LocationState locState;
	int daPointNetId;
	int initialWiFiState;
	private String resp;
	String curSSID;
	String curBSSID;
	int curRSSI;
	String myMAC;
	String myIP;

	String routerIP;
	int beaconFreq;
	int checkStateFreq;

	private static final String TAG = "DaPoint";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_location);

		sPref = getSharedPreferences(getResources().getString(R.string.preferences), MODE_PRIVATE);
		loginName = sPref.getString(getResources().getString(R.string.login_name), "");
		echo(loginName);

		tvHello = (TextView) findViewById(R.id.textViewHello);
		tvHello.setText("Hello, " + loginName);
		tvLog = (TextView) findViewById(R.id.textViewWIFILog);
		tvState = (TextView) findViewById(R.id.textViewState);
		daButton = (Button) findViewById(R.id.buttonDa);
		changeButton = (Button) findViewById(R.id.buttonChangeState);

		settingsEditTextRouterIP = (EditText) findViewById(R.id.editTextRouterIP);
		settingsEditTextBeaconFreq = (EditText) findViewById(R.id.editTextBeaconFreq);
		settingsButtonSaveSettings = (Button) findViewById(R.id.buttonSaveSettings);
		settingsButtonSaveSettings.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				settingsUpdate();
			}
		});
		settingsButtonSettingsResetFields = (Button) findViewById(R.id.buttonSettingsResetFields);
		settingsButtonSettingsResetFields.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				settingsFieldsReset();
			}
		});

		checkStateFreq = 1000;

		settingsUpdate();

		setState(LocationState.S_NA);

		saveInitialWiFiState();

		connectToDaPoint(null);
	}

	Thread beaconSender = null;
	Thread stateChecker = null;

	@Override
	protected void onPause() {
		super.onPause();

		if (beaconSender != null) {
			beaconSender.interrupt();
			beaconSender = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		beaconSender = new Thread() {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Thread.sleep(beaconFreq);
						echo("ping");
						sendRequest("ping");
					}
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		};
		beaconSender.start();
		stateChecker = new Thread() {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Thread.sleep(checkStateFreq);
						echo("check state");
						sendRequest("state?mac=" + myMAC);
					}
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		};
		stateChecker.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return item.getItemId() == R.id.action_settings || super.onOptionsItemSelected(item);
	}

	@Override
	public void onDestroy() {
		setInitialWiFiState();
		super.onDestroy();
	}

	void echo(String msg) {
		Log.d(TAG, msg);
		//Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}

	void settingsUpdate() {
		routerIP = settingsEditTextRouterIP.getText().toString();
		try {
			beaconFreq = Integer.parseInt(settingsEditTextBeaconFreq.getText().toString());
		} catch (RuntimeException e) {
			echo("RE " + e.getMessage());
			e.printStackTrace();
		}
	}

	void settingsFieldsReset() {
		settingsEditTextRouterIP.setText(routerIP);
		settingsEditTextBeaconFreq.setText("" + beaconFreq);
	}

	class ReqSender implements Runnable {
		private final String url;

		public ReqSender(String url) {
			this.url = url;
		}

		public void run() {
			echo("--> " + routerIP);
			try {
				//String url = String.format(pattern, routerIP, exe, comm);
				HttpClient cl = new DefaultHttpClient();
				HttpGet g = new HttpGet(url);
				HttpResponse r = cl.execute(g);
				resp = EntityUtils.toString(r.getEntity(), "UTF-8");
				//resp = r.toString();
				echo("got response: " + resp);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void sendRequest(String path) {
		new Thread(new ReqSender("http://" + routerIP + "/" + path)).start();
	}

	public void sendRequest(String exe, String comm) {
		sendRequest("/" + exe + "/" + comm);
	}

	public void switchLed(View v) {
		echo("switch led");
		sendRequest("barrier", "toggle");
	}

	public void daClick(View v) {
		echo("daClick");
		//new Thread(new ReqSender("led", "toggle")).start();
		sendRequest("barrier", "toggle");
	}

	public enum LocationState {
		S_NA,        // wifi do not connected to dapoint
		S_CONNECTED, // wifi connected to dapoint
		S_READY      // ready to open
	}
	public void setState(LocationState s) {
		if (locState == null || !locState.equals(s)) {
			locState = s;
			switch (s) {
				case S_CONNECTED:
					sendRequest("set_user", loginName);
					daButton.setEnabled(false);
					daButton.setBackgroundColor(Color.parseColor("#FFDDDD"));
					tvState.setText("Connected");
					echo("S_CONNECTED");
					break;
				case S_READY:
					daButton.setEnabled(true);
					daButton.setBackgroundColor(Color.parseColor("#55FF55"));
					tvState.setText("Ready");
					echo("S_READY");
					break;
				case S_NA:
				default:
					daButton.setEnabled(false);
					daButton.setBackgroundColor(Color.parseColor("#DDDDDD"));
					tvState.setText("N/A");
					echo("S_NA");
					break;
			}
		}
	}
	public void stateChanger(View v) {
		try {
			switch(locState) {
				case S_CONNECTED:
					setState(LocationState.S_READY);
					break;
				case S_READY:
					setState(LocationState.S_NA);
					break;
				case S_NA:
					setState(LocationState.S_CONNECTED);
					break;
			}
		} catch(Exception e) {
			echo("exception");
		}

	}

	public void connectToDaPoint(View v) {
		if (checkConnection()) {
			if (curRSSI < -30) {
				setState(LocationState.S_CONNECTED);
			} else {
				setState(LocationState.S_READY);
			}
		} else {
			addDaPointConfig();
			WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
			ScanResult daPointRes = null;
			WifiConfiguration daPointNet = null;
			String log = "";
			wm.setWifiEnabled(true);
			wm.startScan();
			List<ScanResult> srs = wm.getScanResults();
			List<WifiConfiguration> wc = wm.getConfiguredNetworks();

			if (wc != null) {
				for (WifiConfiguration r : wc) {
					if (r.SSID.length() > 2) {
						if (r.SSID.equals("\"DaPoint\"")) {
							daPointNet = r;
						}
					}
				}
			}
			for (ScanResult r : srs) {
				log += r.SSID + " ";
				if (r.SSID.equals("DaPoint")) {
					daPointRes = r;
				}
			}

			log += "\nnet: " + (daPointNet != null) + ", res: " + (daPointRes != null);
			tvLog.setText("connected to " + curSSID + " rssi=" + curRSSI + "\nwifi: " + log);
			if (daPointNet != null && daPointRes != null) {
				wm.disconnect();
				wm.enableNetwork(daPointNet.networkId, true);
				wm.reconnect();
				echo("found DaPoint");
			} else {
				echo("don`t found DaPoint");
			}
		}
	}

	public void addDaPointConfig() {
		WifiConfiguration wc = new WifiConfiguration();
		wc.SSID = "\"DaPoint\"";
		wc.preSharedKey = "\"buranneo\"";
		wc.status = WifiConfiguration.Status.ENABLED;
		wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
		wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
		wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
		wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
		wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
		WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		daPointNetId = wm.addNetwork(wc);
	}

	public void saveInitialWiFiState() {
		WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		initialWiFiState = wm.getWifiState();
		echo("WiFi state: " + initialWiFiState);
	}
	public void setInitialWiFiState() {
		WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		switch (initialWiFiState) {
			case WifiManager.WIFI_STATE_DISABLED:
			case WifiManager.WIFI_STATE_DISABLING:
			case WifiManager.WIFI_STATE_UNKNOWN:
				wm.setWifiEnabled(false);
				break;
			case WifiManager.WIFI_STATE_ENABLED:
			case WifiManager.WIFI_STATE_ENABLING:
				wm.setWifiEnabled(true);
				break;
		}
	}


	public boolean checkConnection() {
		WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		WifiInfo wi = wm.getConnectionInfo();
		curSSID = wi.getSSID();
		curBSSID = wi.getBSSID();
		curRSSI = wi.getRssi();
		myMAC = wi.getMacAddress();
		myIP = "";
		if (curSSID != null) {
			int myIp = wi.getIpAddress();

			int intMyIp3 = myIp/0x1000000;
			int intMyIp3mod = myIp%0x1000000;

			int intMyIp2 = intMyIp3mod/0x10000;
			int intMyIp2mod = intMyIp3mod%0x10000;

			int intMyIp1 = intMyIp2mod/0x100;
			int intMyIp0 = intMyIp2mod%0x100;

			myIP = String.valueOf(intMyIp0)
					+ "." + String.valueOf(intMyIp1)
					+ "." + String.valueOf(intMyIp2)
					+ "." + String.valueOf(intMyIp3);

			tvLog.setText("connected to " + curSSID + " rssi=" + curRSSI + "\nMAC = " + myMAC + "\nIP = " + myIP);
			echo("connected to " + curSSID);
			return curSSID.equals("\"DaPoint\"");
		} else {
			myIP = "";
			echo("noConnection");
			return false;
		}
	}
}
