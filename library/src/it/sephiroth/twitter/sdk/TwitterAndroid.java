package it.sephiroth.twitter.sdk;

import java.util.Iterator;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class TwitterAndroid {

	private static final String LOG_TAG = "TwitterAndroidSDK";

	protected static final String ACCESS_TOKEN = "access_token";

	protected static final String SECRET_TOKEN = "secret_token";

	protected static final String USER_ID = "user_id";

	private static final String PREF_NAME = "TwitterAndroidSDK-Pref";

	private static final String PREF_KEY_LOGGED = "logged_in";
	private static final String PREF_KEY_OAUTH_TOKEN = "oauth_token";
	private static final String PREF_KEY_OAUTH_SECRET = "oauth_token_secret";
	private static final String PREF_KEY_OAUTH_USERID = "oauth_token_userid";
	private static final String PREF_KEY_OAUTH_SCREENNAME = "oauth_screen_name";

	private static final int ACTION_SEND_CALLBACK = 2;

	private final Twitter twitter;

	private final SharedPreferences prefs;
	private final Handler handler;
	private final StatusCallback statusCallback;

	public static final class Session {

		private final AccessToken mAccessToken;
		private final long mUserId;
		private final String mScreenName;

		public Session( AccessToken accessToken, long userId, String screenName ) {
			mAccessToken = accessToken;
			mUserId = userId;
			mScreenName = screenName;
		}

		public AccessToken getAccessToken() {
			return mAccessToken;
		}

		public long getUserId() {
			return mUserId;
		}

		public String getScreenName() {
			return mScreenName;
		}
	}

	public static final class SessionState {

		public static final int INVALID = -1; 
		public static final int DISCONNECTED = 0;
		public static final int CONNECTING = 1;
		public static final int CONNECTED = 2;
		public static final int LOGIN_FAILED = 3;

		private int mState;
		private Session mSession;
		private TwitterException mException;

		SessionState( int state ) {
			mState = state;
		}

		public TwitterException getException() {
			return mException;
		}

		public int getState() {
			return mState;
		}

		public Session getActiveSession() {
			return mSession;
		}

		private static SessionState create( int state, Session session ) {
			SessionState instance = new SessionState( state );
			instance.mSession = session;
			return instance;
		}

		private static SessionState create( int state, TwitterException exception ) {
			SessionState instance = new SessionState( state );
			instance.mException = exception;
			return instance;
		}
	}

	private SessionState mSessionState;

	public static interface StatusCallback {

		public void call( SessionState newState );
	}

	private Handler.Callback handlerCallback = new Handler.Callback() {

		@Override
		public boolean handleMessage( Message msg ) {
			final int action = msg.what;

			Log.i( LOG_TAG, "handleMessage: " + action );

			switch ( action ) {
				case ACTION_SEND_CALLBACK:
					SessionState state = (SessionState) msg.obj;
					statusCallback.call( state );
					break;
			}

			return false;
		}
	};

	public TwitterAndroid( Context context, StatusCallback callback, Configuration configuration ) {
		TwitterFactory factory = new TwitterFactory( configuration );
		twitter = factory.getInstance();
		prefs = context.getApplicationContext().getSharedPreferences( PREF_NAME, Context.MODE_PRIVATE );
		handler = new Handler( handlerCallback );
		statusCallback = callback;

		mSessionState = new SessionState( SessionState.INVALID );

		AccessToken accessToken = getSavedAccessToken();
		Log.i( LOG_TAG, "saved accessToken: " + accessToken );

		if ( null != accessToken ) {
			performAutoLogin( accessToken );
		} else {
			setSessionState( SessionState.DISCONNECTED );
		}
	}

	private boolean setSessionState( int state ) {
		return setSessionState( new SessionState( state ) );
	}

	private synchronized boolean setSessionState( SessionState newState ) {

		Log.i( LOG_TAG, "setStatus: " + newState.getState() + " from " + mSessionState.getState() );
		
		final int old_state = mSessionState.getState();
		final int new_state = newState.getState();

		boolean handled = false;

		if ( old_state != new_state ) {

			switch ( new_state ) {
				case SessionState.CONNECTING:
					if ( old_state != SessionState.CONNECTING ) {
						handled = true;
					}
					break;

				case SessionState.CONNECTED:
					if ( old_state == SessionState.CONNECTING ) {

						Session session = newState.getActiveSession();
						AccessToken accessToken = session.getAccessToken();
						saveAccessToken( accessToken.getToken(), accessToken.getTokenSecret(), session.getUserId(), session.getScreenName() );
						
						Log.e( LOG_TAG, "setAccessToken: " + accessToken );
						twitter.setOAuthAccessToken( accessToken );
						handled = true;
					}
					break;

				case SessionState.DISCONNECTED:
					if ( old_state != SessionState.DISCONNECTED ) {
						clearAccessToken();
						handled = true;
					}
					break;

				case SessionState.LOGIN_FAILED:
					if ( old_state == SessionState.CONNECTING ) {
						clearAccessToken();
						handled = true;
					}
					break;
			}
		}

		if ( handled ) {
			mSessionState = newState;
			sendCallback( mSessionState );
		} else {
			Log.e( LOG_TAG, "error, " + new_state + " == old state" );
		}
		return handled;
	}

	private synchronized SessionState getSessionState() {
		return mSessionState;
	}

	private void sendCallback( SessionState state ) {
		Log.i( LOG_TAG, "sendCallback: " + state.getState() );
		Message message = handler.obtainMessage( ACTION_SEND_CALLBACK, state );
		handler.sendMessage( message );
	}

	public void logout() {
		Log.i( LOG_TAG, "logout" );
		setSessionState( SessionState.DISCONNECTED );
	}

	public void login( Context context ) {
		Log.i( LOG_TAG, "login" );

		if ( setSessionState( SessionState.CONNECTING ) ) {

			DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {

				@Override
				public void onCancel( DialogInterface dialog ) {
					Log.i( LOG_TAG, "onCancel" );
					( (TwitterDialog) dialog ).setOnTwitterDialogListener( null );
					logout();
				}
			};

			TwitterDialog.DialogCallback twitterDialogCallback = new TwitterDialog.DialogCallback() {

				@Override
				public void onAutorizationComplete( AccessToken accessToken ) {
					Log.i( LOG_TAG, "onAuthorizationComplete: " + accessToken );
					
					SessionState sessionState = SessionState.create( SessionState.CONNECTED,
							new Session( accessToken, accessToken.getUserId(), accessToken.getScreenName() ) );
					setSessionState( sessionState );
				}

				@Override
				public void onAuthorizationDenied() {
					Log.i( LOG_TAG, "onAuthorizationDenied" );
					logout();
				}
			};

			final TwitterDialog dialog = new TwitterDialog( context, twitter.getConfiguration() );
			dialog.setOnTwitterDialogListener( twitterDialogCallback );
			dialog.setOnCancelListener( cancelListener );
			dialog.show();
		}
	}

	private void performAutoLogin( final AccessToken token ) {
		Log.i( LOG_TAG, "performAutoLogin: " + token );

		if ( setSessionState( SessionState.CONNECTING ) ) {

			new Thread( new Runnable() {

				@Override
				public void run() {

					twitter.setOAuthAccessToken( token );

					try {
						String screen_name = twitter.getScreenName();
						long userId = token.getUserId();

						SessionState sessionState = SessionState
								.create( SessionState.CONNECTED, new Session( token, userId, screen_name ) );
						setSessionState( sessionState );

					} catch ( TwitterException e ) {

						e.printStackTrace();

						if ( e.isCausedByNetworkIssue() ) {
							String screenName = getSavedScreenName();
							long userId = token.getUserId();

							if ( null != screenName && userId > 0 ) {

								SessionState sessionState = SessionState.create( SessionState.CONNECTED, new Session( token, userId,
										screenName ) );
								setSessionState( sessionState );
							}
							return;
						}

						Log.d( LOG_TAG, "network error: " + e.isCausedByNetworkIssue() );
						Log.d( LOG_TAG, "error code: " + e.getErrorCode() );
						Log.d( LOG_TAG, "exception code: " + e.getExceptionCode() );

						SessionState sessionState = SessionState.create( SessionState.LOGIN_FAILED, e );
						setSessionState( sessionState );
					}

				}
			} ).start();
		} else {
			setSessionState( SessionState.DISCONNECTED );
		}
	}

	public boolean isLogged() {
		Log.i( LOG_TAG, "isLogged" );
		return getSessionState().getState() == SessionState.CONNECTED;
	}

	private void saveAccessToken( String token, String tokenSecret, long userId, String screenName ) {
		Log.i( LOG_TAG, "saveAccessToken: " + token + ", " + tokenSecret + ", " + userId + ", " + screenName );

		Editor editor = prefs.edit();
		editor.putString( PREF_KEY_OAUTH_TOKEN, token );
		editor.putString( PREF_KEY_OAUTH_SECRET, tokenSecret );
		editor.putString( PREF_KEY_OAUTH_SCREENNAME, screenName );
		editor.putLong( PREF_KEY_OAUTH_USERID, userId );
		editor.putBoolean( PREF_KEY_LOGGED, true );
		editor.commit();
	}

	private void clearAccessToken() {
		Log.i( LOG_TAG, "clearAccessToken" );

		Log.e( LOG_TAG, "setAccessToken: " + null );
		twitter.setOAuthAccessToken( null );
		
		Editor editor = prefs.edit();
		editor.remove( PREF_KEY_LOGGED );
		editor.remove( PREF_KEY_OAUTH_SECRET );
		editor.remove( PREF_KEY_OAUTH_TOKEN );
		editor.remove( PREF_KEY_OAUTH_USERID );
		editor.remove( PREF_KEY_OAUTH_SCREENNAME );
		editor.commit();
	}

	private AccessToken getSavedAccessToken() {
		String token = prefs.getString( PREF_KEY_OAUTH_TOKEN, null );
		String secret = prefs.getString( PREF_KEY_OAUTH_SECRET, null );
		String screenName = getSavedScreenName();
		long userId = prefs.getLong( PREF_KEY_OAUTH_USERID, 0 );

		if ( userId > 0 && token != null && secret != null && screenName != null ) {
			return new AccessToken( token, secret, userId );
		}
		return null;
	}

	private String getSavedScreenName() {
		return prefs.getString( PREF_KEY_OAUTH_SCREENNAME, null );
	}
	
	public void loadTimeline() {
		Log.i( LOG_TAG, "loadTimeline" );
		if( isLogged() ) {
			Log.i( LOG_TAG, "[ok] loadTimeline" );
			new Thread( new Runnable() {
				
				@Override
				public void run() {
					try {
						ResponseList<Status> result = twitter.getUserTimeline();
						Log.d( LOG_TAG, "result: " + result.size() );
						
						Iterator<Status> iterator = result.iterator();
						
						int index = 0;
						while( iterator.hasNext() ) {
							Status item = iterator.next();
							Log.d( LOG_TAG, "status: " + ++index + " = " + item.getText() );
						}
					} catch ( TwitterException e ) {
						e.printStackTrace();
					}
				}
			} ).start();
		}
	}
}
