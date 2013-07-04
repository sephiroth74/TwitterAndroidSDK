package it.sephiroth.twitter.sdk;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;


public class TwitterDialog extends Dialog {

	private static final String LOG_TAG = "twitter-dialog";
	
	private static final String DENIED_QUERY = "denied";
	private static final String OAUTH_TOKEN_QUERY = "oauth_token";
	private static final String OAUTH_TOKEN_VERIFIER_QUERY = "oauth_verifier";
	
	private static final float[] DIMENSIONS_LANDSCAPE = { 460, 260 };
	private static final float[] DIMENSIONS_PORTRAIT = { 280, 430 };
	
	private static final int ACTION_LOAD_PAGE_URL = 1;
	private static final int ACTION_DISMISS = 2;
	
	private Twitter twitter;
	private WebView webView;
	private ProgressBar spinner;
	private Uri authorizationUri;
	private final Handler.Callback callback = new Handler.Callback() {
		
		@Override
		public boolean handleMessage( Message msg ) {
			Log.i( LOG_TAG, "handleMessage: " + msg.what );
			
			if( !isShowing() || null == getWindow() ) return false;
			
			switch( msg.what ) {
				case ACTION_LOAD_PAGE_URL:
					webView.loadUrl( (String) msg.obj );
					return true;
					
				case ACTION_DISMISS:
					dismiss();
					return true;
			}
			
			return false;
		}
	};
	
	private final Handler handler = new Handler( callback );
	private DialogCallback dialogCallback;
	
	static interface DialogCallback {
		public void onAutorizationComplete( AccessToken accessToken );
		public void onAuthorizationDenied();
	}
	
	public TwitterDialog( Context context, Configuration configuration ) {
		super( context );
		twitter = new TwitterFactory( configuration ).getInstance();
	}
	
	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		Log.i( LOG_TAG, "onCreate" );
		
		super.onCreate( savedInstanceState );
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		
		
		webView = new WebView( getContext() );
		webView.setVerticalScrollBarEnabled( false );
		webView.setHorizontalScrollBarEnabled( false );
		webView.setWebViewClient( new CustomClient() );
		webView.getSettings().setJavaScriptEnabled( true );
		webView.setLayoutParams( new FrameLayout.LayoutParams( FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT ) );
		webView.getSettings().setSavePassword( false );
		
		spinner = new ProgressBar( getContext(), null, android.R.attr.progressBarStyle );
		spinner.setIndeterminate( true );
		
		RelativeLayout layout = new RelativeLayout( getContext() );
		
		DisplayMetrics display = getContext().getResources().getDisplayMetrics();
		final float scale = getContext().getResources().getDisplayMetrics().density;
		float[] dimensions = display.widthPixels < display.heightPixels ? DIMENSIONS_PORTRAIT : DIMENSIONS_LANDSCAPE;
		final int w = (int) ( dimensions[0] * scale + 0.5f );
		final int h = (int) ( dimensions[1] * scale + 0.5f );		

		addContentView( layout, new LayoutParams( w, h ) );
		
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT );
		layout.addView( webView, params );
		
		params = new RelativeLayout.LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
		params.addRule( RelativeLayout.CENTER_IN_PARENT );
		layout.addView( spinner, params );
		
		initialize();
	}
	
	private void initialize() {
		Log.i( LOG_TAG, "initialize" );
		
		startLoading();

		new Thread( new Runnable() {

			@Override
			public void run() {
				RequestToken requestToken;
				try {
					
					requestToken = twitter.getOAuthRequestToken();
					final String url = requestToken.getAuthorizationURL();
					Log.i( LOG_TAG, "url: " + url );
					
					authorizationUri = Uri.parse( url );
					handler.sendMessage( handler.obtainMessage( ACTION_LOAD_PAGE_URL, url ) );

				} catch ( TwitterException e ) {
					e.printStackTrace();
				}
			}
		} ).start();		
	}
	
	private void retrieveAccessToken( final String token, final String verifier ) {
		Log.i( LOG_TAG, "retrieveAccessToken: " + token + " -- " + verifier );
		startLoading();
		
		new Thread( new Runnable() {
			
			@Override
			public void run() {
				String access_token_secret = twitter.getConfiguration().getOAuthAccessTokenSecret();
				Log.d( LOG_TAG, "access token secret: " + access_token_secret );
				AccessToken accessToken;
				try {
					accessToken = twitter.getOAuthAccessToken( new RequestToken( token, access_token_secret ), verifier );
					fireAuthorizationComplete( accessToken );
				} catch ( TwitterException e ) {
					e.printStackTrace();
				}
				
				handler.sendEmptyMessage( ACTION_DISMISS );
			}
		} ).start();
	}
	
	private void fireAuthorizationDenied() {
		Log.i( LOG_TAG, "fireAuthorizationDenied" );
		if( null != dialogCallback ) {
			dialogCallback.onAuthorizationDenied();
		}
	}
	
	private void fireAuthorizationComplete( AccessToken accessToken ) {
		Log.i( LOG_TAG, "fireAuthorizationComplete: " + accessToken );
		if( null != dialogCallback ) {
			dialogCallback.onAutorizationComplete( accessToken );
		}
	}
	
	private void startLoading() {
		spinner.setVisibility( View.VISIBLE );
	}
	
	private void endLoading() {
		spinner.setVisibility( View.GONE );
	}
	
	public void setOnTwitterDialogListener( DialogCallback callback ) {
		dialogCallback = callback;
	}
	
	@Override
	public void onContentChanged() {
		Log.i( LOG_TAG, "onContentChanged" );
		super.onContentChanged();
	}
	
	private class CustomClient extends WebViewClient {
		
		private String LOG_TAG = "custom-client";
		
		@Override
		public void onPageStarted( WebView view, String url, Bitmap favicon ) {
			Log.i( LOG_TAG, "onPageStarted: " + url );
			super.onPageStarted( view, url, favicon );
			startLoading();
		}
		
		@Override
		public void onPageFinished( WebView view, String url ) {
			Log.i( LOG_TAG, "onPageFinished: " + url );
			super.onPageFinished( view, url );
			endLoading();
		}
		
		@Override
		public void onReceivedError( WebView view, int errorCode, String description, String failingUrl ) {
			Log.e( LOG_TAG, "onReceivedError: " + failingUrl + ", code: "+ errorCode + " = " + description );
			super.onReceivedError( view, errorCode, description, failingUrl );
		}
		
		@Override
		public boolean shouldOverrideUrlLoading( WebView view, String url ) {
			Log.i( LOG_TAG, "shouldOverrideUrlLoading: " + url );
			
			Uri uri = Uri.parse( url );
			String denied_params = uri.getQueryParameter( DENIED_QUERY );
			String oauth_token = uri.getQueryParameter( OAUTH_TOKEN_QUERY );
			String oauth_token_verifier = uri.getQueryParameter( OAUTH_TOKEN_VERIFIER_QUERY );
			
			if( !authorizationUri.getHost().equals( uri.getHost() )) {
				if( null != denied_params ) {
					fireAuthorizationDenied();
					dismiss();
					return true;
				} else if( null != oauth_token && null != oauth_token_verifier ) {
					Log.d( LOG_TAG, "token: " + oauth_token + ", verifier: " + oauth_token_verifier );
					retrieveAccessToken( oauth_token, oauth_token_verifier );
					return true;
				}
			}
			
			return super.shouldOverrideUrlLoading( view, url );
		}
	}
}
