package com.example.twitter_demo;

import it.sephiroth.twitter.sdk.TwitterAndroid;
import it.sephiroth.twitter.sdk.TwitterAndroid.Session;
import it.sephiroth.twitter.sdk.TwitterAndroid.SessionState;
import it.sephiroth.twitter.sdk.TwitterAndroid.StatusCallback;
import twitter4j.TwitterException;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {
	
	protected static final String LOG_TAG = "main-activity";
	
	private TwitterAndroid twitter;

	private TextView text;
	private Button button;
	private Button button2;
	
	private StatusCallback statusCallback = new StatusCallback() {
		
		@Override
		public void call( SessionState newState ) {
			Log.i( LOG_TAG, "statuscallback::call: " + newState );
			
			TwitterException exception = newState.getException();
			int state = newState.getState();
			
			if( null != exception ) {
				Log.w( LOG_TAG, "exception: " + exception.getErrorMessage() );
				return;
			}
			
			switch( state ) {
				case SessionState.LOGIN_FAILED:
					setProgressBarIndeterminateVisibility( false );
					onLogout();
					break;
					
				case SessionState.CONNECTED:
					setProgressBarIndeterminateVisibility( false );
					onLoggedIn( newState.getActiveSession() );
					break;
					
				case SessionState.DISCONNECTED:
					setProgressBarIndeterminateVisibility( false );
					onLogout();
					break;
					
				case SessionState.CONNECTING:
					setProgressBarIndeterminateVisibility( true );
					break;
			}
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		requestWindowFeature( Window.FEATURE_INDETERMINATE_PROGRESS );
		setContentView( R.layout.activity_main );
		setProgressBarIndeterminateVisibility( false );
		
		// Note that these values are not included in this project
		String consumer_key = getString( R.string.twitter_consumer_key );
		String consumer_key_secret = getString( R.string.twitter_consumer_key_secret );
		String access_token_secret = getString( R.string.twitter_access_token_secret );
		
		ConfigurationBuilder builder = new ConfigurationBuilder();
		builder.setOAuthConsumerKey( consumer_key );
		builder.setOAuthConsumerSecret( consumer_key_secret );
		builder.setOAuthAccessTokenSecret( access_token_secret );
		Configuration configuration = builder.build();
		
		twitter = new TwitterAndroid( this, statusCallback, configuration );
		button.setOnClickListener( this );
		button2.setOnClickListener( this );
	}
	
	@Override
	public void onContentChanged() {
		super.onContentChanged();
		
		button = (Button) findViewById( R.id.button1 );
		button2 = (Button) findViewById( R.id.button2 );
		text = (TextView) findViewById( R.id.textView1 );
	}
	
	private void onLogout() {
		button.setEnabled( true );
		button2.setEnabled( false );
		button.setText( "Login" );
		text.setText( "not logged" );
	}
	
	private void onLoggedIn( Session session ) {
		button.setEnabled( true );
		button2.setEnabled( true );
		button.setText( "Logout" );
		text.setText( "Welcome, " + session.getScreenName() );
	}

	@Override
	public void onClick( View v ) {
		final int id = v.getId();
		if( id == button.getId() ) {
			
			if( twitter.isLogged() ) {
				twitter.logout();
			} else {
				twitter.login( this );
			}
		} else if( id == button2.getId() ) {
			twitter.loadTimeline();
		}
	}

}
