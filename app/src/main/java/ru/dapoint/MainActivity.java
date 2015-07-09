package ru.dapoint;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import ru.dapoint.R;


public class MainActivity extends ActionBarActivity implements OnClickListener {

	Button btnLogin;
	EditText etLogin;
	EditText etPassword;
	String loginState;
	PassChecker checker;
	
	SharedPreferences sPref;

	private static final String TAG = "myLogsLogin";
	
	class PassChecker extends AsyncTask<String, Integer, String> {
		private static final String URL_FORMAT = "http://%s/check_user.php?username=%s&password=%s";
		private String host;
		private String tryLogin;
				
		@Override
	    protected void onPreExecute() {
			super.onPreExecute();
			host = getResources().getString(R.string.host);
		}
		
		@Override
	    protected String doInBackground(String... params) {
			String loginStateLocal;
			tryLogin = params[0];
			try {
		    	String url = String.format(URL_FORMAT, host, params[0], params[1]); 
		    	Log.d(TAG, url);
		    	HttpClient cl = new DefaultHttpClient();
		    	HttpGet g = new HttpGet(url);
				HttpResponse r = cl.execute(g);
				loginStateLocal = EntityUtils.toString(r.getEntity(), "UTF-8");
			} catch (Exception e) {
				loginStateLocal = "exception";
				e.printStackTrace();
			}
			Log.d(TAG, loginStateLocal);
			return loginStateLocal;
		}

		@Override
	    protected void onPostExecute(String res) {
			super.onPostExecute(res);
			echo("onPostExecute");
			loginState = res;
			putLoginState(res, tryLogin);
			if (res.equals("ok")) {
				onCorrectPassword();
			} else {
				onInCorrectPassword();
				
			}
		}

	};
	
	void echo(String msg) {
		Log.d(TAG, msg);
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}

	void putLoginState(String state, String login) {
		sPref = getSharedPreferences(getResources().getString(R.string.preferences), MODE_PRIVATE);
	    Editor ed = sPref.edit();
	    ed.putString(getResources().getString(R.string.login_state), state);
	    ed.putString(getResources().getString(R.string.login_name), login);
	    ed.commit();
	}
	void checkLoginState() {
		sPref = getSharedPreferences(getResources().getString(R.string.preferences), MODE_PRIVATE);
	    loginState = sPref.getString(getResources().getString(R.string.login_state), "");
	    echo(loginState);
	    if (loginState.equals("ok")) {
	    	onCorrectPassword();
	    } else {
	    	echo("strange");
	    }
	    
	}
	
	void onCorrectPassword() {
		echo("onCorrectPassword");
		Intent intent = new Intent(this, LocationActivity.class);
	    startActivity(intent);
	}
	
	void onInCorrectPassword() {
		echo("onInCorrectPassword\n" + loginState);
	}
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btnLogin = (Button)findViewById(R.id.buttonLogin);
        btnLogin.setOnClickListener(this);
        
        etLogin = (EditText)findViewById(R.id.editTextLogin);
        etPassword = (EditText)findViewById(R.id.editTextPassword);
        
        checkLoginState();
    }

    @Override
    public void onClick(View v) {
    	String login = etLogin.getText().toString();
    	String password = etPassword.getText().toString();
    	checker = new PassChecker();
    	checker.execute(login, password);
    }

}
