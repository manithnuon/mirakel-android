/*
 * Copyright (C) 2011-2013 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sufficientlysecure.donations;

import org.sufficientlysecure.donations.google.util.IabHelper;
import org.sufficientlysecure.donations.google.util.IabResult;
import org.sufficientlysecure.donations.google.util.Purchase;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import de.azapps.mirakel.donationslib.R;

public class DonationsFragment extends Fragment {

	public static final String ARG_DEBUG = "debug";

	public static final String ARG_GOOGLE_ENABLED = "googleEnabled";
	public static final String ARG_GOOGLE_PUBKEY = "googlePubkey";
	public static final String ARG_GOOGLE_CATALOG = "googleCatalog";
	public static final String ARG_GOOGLE_CATALOG_VALUES = "googleCatalogValues";

	public static final String ARG_PAYPAL_ENABLED = "paypalEnabled";
	public static final String ARG_PAYPAL_USER = "paypalUser";
	public static final String ARG_PAYPAL_CURRENCY_CODE = "paypalCurrencyCode";
	public static final String ARG_PAYPAL_ITEM_NAME = "mPaypalItemName";

	public static final String ARG_FLATTR_ENABLED = "flattrEnabled";
	public static final String ARG_FLATTR_PROJECT_URL = "flattrProjectUrl";
	public static final String ARG_FLATTR_URL = "flattrUrl";

	private static final String TAG = "Donations Library";

	// http://developer.android.com/google/play/billing/billing_testing.html
	private static final String[] CATALOG_DEBUG = new String[] {
			"android.test.purchased", "android.test.canceled",
			"android.test.refunded", "android.test.item_unavailable" };

	private Spinner mGoogleSpinner;
	private TextView mFlattrUrlTextView;

	// Google Play helper object
	protected IabHelper mHelper;

	protected boolean mDebug = false;

	protected boolean mGoogleEnabled = false;
	protected String mGooglePubkey = "";
	protected String[] mGgoogleCatalog = new String[] {};
	protected String[] mGoogleCatalogValues = new String[] {};

	protected boolean mPaypalEnabled = false;
	protected String mPaypalUser = "";
	protected String mPaypalCurrencyCode = "";
	protected String mPaypalItemName = "";

	protected boolean mFlattrEnabled = false;
	protected String mFlattrProjectUrl = "";
	protected String mFlattrUrl = "";

	/**
	 * Instantiate DonationsFragment.
	 * 
	 * @param debug
	 *            You can use BuildHelper.DEBUG to propagate the debug flag from
	 *            your app to the Donations library
	 * @param googleEnabled
	 *            Enabled Google Play donations
	 * @param googlePubkey
	 *            Your Google Play public key
	 * @param googleCatalog
	 *            Possible item names that can be purchased from Google Play
	 * @param googleCatalogValues
	 *            Values for the names
	 * @param paypalEnabled
	 *            Enable PayPal donations
	 * @param paypalUser
	 *            Your PayPal email address
	 * @param paypalCurrencyCode
	 *            Currency code like EUR. See here for other codes:
	 *            https://developer
	 *            .paypal.com/webapps/developer/docs/classic/api
	 *            /currency_codes/#id09A6G0U0GYK
	 * @param paypalItemName
	 *            Display item name on PayPal, like "Donation for NTPSync"
	 * @param flattrEnabled
	 *            Enable Flattr donations
	 * @param flattrProjectUrl
	 *            The project URL used on Flattr
	 * @param flattrUrl
	 *            The Flattr URL to your thing. NOTE: Enter without http://
	 * @return DonationsFragment
	 */
	public static DonationsFragment newInstance(final boolean debug,
			final boolean googleEnabled, final String googlePubkey,
			final String[] googleCatalog, final String[] googleCatalogValues,
			final boolean paypalEnabled, final String paypalUser,
			final String paypalCurrencyCode, final String paypalItemName,
			final boolean flattrEnabled, final String flattrProjectUrl,
			final String flattrUrl) {
		final DonationsFragment donationsFragment = new DonationsFragment();
		final Bundle args = new Bundle();

		args.putBoolean(ARG_DEBUG, debug);

		args.putBoolean(ARG_GOOGLE_ENABLED, googleEnabled);
		args.putString(ARG_GOOGLE_PUBKEY, googlePubkey);
		args.putStringArray(ARG_GOOGLE_CATALOG, googleCatalog);
		args.putStringArray(ARG_GOOGLE_CATALOG_VALUES, googleCatalogValues);

		args.putBoolean(ARG_PAYPAL_ENABLED, paypalEnabled);
		args.putString(ARG_PAYPAL_USER, paypalUser);
		args.putString(ARG_PAYPAL_CURRENCY_CODE, paypalCurrencyCode);
		args.putString(ARG_PAYPAL_ITEM_NAME, paypalItemName);

		args.putBoolean(ARG_FLATTR_ENABLED, flattrEnabled);
		args.putString(ARG_FLATTR_PROJECT_URL, flattrProjectUrl);
		args.putString(ARG_FLATTR_URL, flattrUrl);

		donationsFragment.setArguments(args);
		return donationsFragment;
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.mDebug = getArguments().getBoolean(ARG_DEBUG);

		this.mGoogleEnabled = getArguments().getBoolean(ARG_GOOGLE_ENABLED);
		this.mGooglePubkey = getArguments().getString(ARG_GOOGLE_PUBKEY);
		this.mGgoogleCatalog = getArguments()
				.getStringArray(ARG_GOOGLE_CATALOG);
		this.mGoogleCatalogValues = getArguments().getStringArray(
				ARG_GOOGLE_CATALOG_VALUES);

		this.mPaypalEnabled = getArguments().getBoolean(ARG_PAYPAL_ENABLED);
		this.mPaypalUser = getArguments().getString(ARG_PAYPAL_USER);
		this.mPaypalCurrencyCode = getArguments().getString(
				ARG_PAYPAL_CURRENCY_CODE);
		this.mPaypalItemName = getArguments().getString(ARG_PAYPAL_ITEM_NAME);

		this.mFlattrEnabled = getArguments().getBoolean(ARG_FLATTR_ENABLED);
		this.mFlattrProjectUrl = getArguments().getString(
				ARG_FLATTR_PROJECT_URL);
		this.mFlattrUrl = getArguments().getString(ARG_FLATTR_URL);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
			final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.donations__fragment,
				container, false);

		return view;
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		/* Flattr */
		if (this.mFlattrEnabled) {
			// inflate flattr view into stub
			final ViewStub flattrViewStub = (ViewStub) getActivity()
					.findViewById(R.id.donations__flattr_stub);
			flattrViewStub.inflate();

			buildFlattrView();
		}

		/* Google */
		if (this.mGoogleEnabled) {
			// inflate google view into stub
			final ViewStub googleViewStub = (ViewStub) getActivity()
					.findViewById(R.id.donations__google_stub);
			googleViewStub.inflate();

			// choose donation amount
			this.mGoogleSpinner = (Spinner) getActivity().findViewById(
					R.id.donations__google_android_market_spinner);
			final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
					getActivity(), android.R.layout.simple_spinner_item,
					this.mGoogleCatalogValues);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			this.mGoogleSpinner.setAdapter(adapter);

			final Button btGoogle = (Button) getActivity().findViewById(
					R.id.donations__google_android_market_donate_button);
			btGoogle.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(final View v) {
					donateGoogleOnClick(v);
				}
			});

			// Create the helper, passing it our context and the public key to
			// verify signatures with
			if (this.mDebug) {
				Log.d(TAG, "Creating IAB helper.");
			}
			this.mHelper = new IabHelper(getActivity(), this.mGooglePubkey);

			// enable debug logging (for a production application, you should
			// set this to false).
			this.mHelper.enableDebugLogging(this.mDebug);

			// Start setup. This is asynchronous and the specified listener
			// will be called once setup completes.
			if (this.mDebug) {
				Log.d(TAG, "Starting setup.");
			}
			this.mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
				@Override
				public void onIabSetupFinished(final IabResult result) {
					if (DonationsFragment.this.mDebug) {
						Log.d(TAG, "Setup finished.");
					}

					if (!result.isSuccess()) {
						// Oh noes, there was a problem.
						openDialog(
								android.R.drawable.ic_dialog_alert,
								R.string.donations__google_android_market_not_supported_title,
								getString(R.string.donations__google_android_market_not_supported));
						return;
					}

					// Have we been disposed of in the meantime? If so, quit.
					if (DonationsFragment.this.mHelper == null) {
						return;
					}
				}
			});
		}

		/* PayPal */
		if (this.mPaypalEnabled) {
			// inflate paypal view into stub
			final ViewStub paypalViewStub = (ViewStub) getActivity()
					.findViewById(R.id.donations__paypal_stub);
			paypalViewStub.inflate();

			final Button btPayPal = (Button) getActivity().findViewById(
					R.id.donations__paypal_donate_button);
			btPayPal.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(final View v) {
					donatePayPalOnClick(v);
				}
			});
		}
	}

	/**
	 * Open dialog
	 * 
	 * @param icon
	 * @param title
	 * @param message
	 */
	void openDialog(final int icon, final int title, final String message) {
		final AlertDialog.Builder dialog = new AlertDialog.Builder(
				getActivity());
		dialog.setIcon(icon);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setCancelable(true);
		dialog.setNeutralButton(R.string.donations__button_close,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog,
							final int which) {
						dialog.dismiss();
					}
				});
		dialog.show();
	}

	/**
	 * Donate button executes donations based on selection in spinner
	 * 
	 * @param view
	 */
	public void donateGoogleOnClick(final View view) {
		final int index;
		index = this.mGoogleSpinner.getSelectedItemPosition();
		if (this.mDebug) {
			Log.d(TAG, "selected item in spinner: " + index);
		}

		if (this.mDebug) {
			// when debugging, choose android.test.x item
			this.mHelper.launchPurchaseFlow(getActivity(),
					CATALOG_DEBUG[index], IabHelper.ITEM_TYPE_INAPP, 0,
					this.mPurchaseFinishedListener, null);
		} else {
			this.mHelper.launchPurchaseFlow(getActivity(),
					this.mGgoogleCatalog[index], IabHelper.ITEM_TYPE_INAPP, 0,
					this.mPurchaseFinishedListener, null);
		}
	}

	// Callback for when a purchase is finished
	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		@Override
		public void onIabPurchaseFinished(final IabResult result,
				final Purchase purchase) {
			if (DonationsFragment.this.mDebug) {
				Log.d(TAG, "Purchase finished: " + result + ", purchase: "
						+ purchase);
			}

			// if we were disposed of in the meantime, quit.
			if (DonationsFragment.this.mHelper == null) {
				return;
			}

			if (result.isSuccess()) {
				if (DonationsFragment.this.mDebug) {
					Log.d(TAG, "Purchase successful.");
				}

				// directly consume in-app purchase, so that people can donate
				// multiple times
				DonationsFragment.this.mHelper.consumeAsync(purchase,
						DonationsFragment.this.mConsumeFinishedListener);

				// show thanks openDialog
				openDialog(android.R.drawable.ic_dialog_info,
						R.string.donations__thanks_dialog_title,
						getString(R.string.donations__thanks_dialog));
			}
		}
	};

	// Called when consumption is complete
	IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
		@Override
		public void onConsumeFinished(final Purchase purchase,
				final IabResult result) {
			if (DonationsFragment.this.mDebug) {
				Log.d(TAG, "Consumption finished. Purchase: " + purchase
						+ ", result: " + result);
			}

			// if we were disposed of in the meantime, quit.
			if (DonationsFragment.this.mHelper == null) {
				return;
			}

			if (result.isSuccess()) {
				if (DonationsFragment.this.mDebug) {
					Log.d(TAG, "Consumption successful. Provisioning.");
				}
			}
			if (DonationsFragment.this.mDebug) {
				Log.d(TAG, "End consumption flow.");
			}
		}
	};

	@Override
	public void onActivityResult(final int requestCode, final int resultCode,
			final Intent data) {
		if (this.mDebug) {
			Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode
					+ "," + data);
		}
		if (this.mHelper == null) {
			return;
		}

		// Pass on the fragment result to the helper for handling
		if (!this.mHelper.handleActivityResult(requestCode, resultCode, data)) {
			// not handled, so handle it ourselves (here's where you'd
			// perform any handling of activity results not related to in-app
			// billing...
			super.onActivityResult(requestCode, resultCode, data);
		} else {
			if (this.mDebug) {
				Log.d(TAG, "onActivityResult handled by IABUtil.");
			}
		}
	}

	/**
	 * Donate button with PayPal by opening browser with defined URL For
	 * possible parameters see:
	 * https://developer.paypal.com/webapps/developer/docs
	 * /classic/paypal-payments
	 * -standard/integration-guide/Appx_websitestandard_htmlvariables/
	 * 
	 * @param view
	 */
	public void donatePayPalOnClick(final View view) {
		final Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.scheme("https").authority("www.paypal.com")
				.path("cgi-bin/webscr");
		uriBuilder.appendQueryParameter("cmd", "_donations");

		uriBuilder.appendQueryParameter("business", this.mPaypalUser);
		uriBuilder.appendQueryParameter("lc", "US");
		uriBuilder.appendQueryParameter("item_name", this.mPaypalItemName);
		uriBuilder.appendQueryParameter("no_note", "1");
		// uriBuilder.appendQueryParameter("no_note", "0");
		// uriBuilder.appendQueryParameter("cn", "Note to the developer");
		uriBuilder.appendQueryParameter("no_shipping", "1");
		uriBuilder.appendQueryParameter("currency_code",
				this.mPaypalCurrencyCode);
		final Uri payPalUri = uriBuilder.build();

		if (this.mDebug) {
			Log.d(TAG,
					"Opening the browser with the url: " + payPalUri.toString());
		}

		// Start your favorite browser
		try {
			final Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
			startActivity(viewIntent);
		} catch (final ActivityNotFoundException e) {
			openDialog(android.R.drawable.ic_dialog_alert,
					R.string.donations__alert_dialog_title,
					getString(R.string.donations__alert_dialog_no_browser));
		}
	}

	/**
	 * Build view for Flattr. see Flattr API for more information:
	 * http://developers.flattr.net/button/
	 */
	@SuppressLint("SetJavaScriptEnabled")
	@TargetApi(11)
	private void buildFlattrView() {
		final FrameLayout mLoadingFrame;
		final WebView mFlattrWebview;

		mFlattrWebview = (WebView) getActivity().findViewById(
				R.id.donations__flattr_webview);
		mLoadingFrame = (FrameLayout) getActivity().findViewById(
				R.id.donations__loading_frame);

		// disable hardware acceleration for this webview to get transparent
		// background working
		if (Build.VERSION.SDK_INT >= 11) {
			mFlattrWebview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}

		// define own webview client to override loading behaviour
		mFlattrWebview.setWebViewClient(new WebViewClient() {
			/**
			 * Open all links in browser, not in webview
			 */
			@Override
			public boolean shouldOverrideUrlLoading(final WebView view,
					final String urlNewString) {
				try {
					view.getContext().startActivity(
							new Intent(Intent.ACTION_VIEW, Uri
									.parse(urlNewString)));
				} catch (final ActivityNotFoundException e) {
					openDialog(
							android.R.drawable.ic_dialog_alert,
							R.string.donations__alert_dialog_title,
							getString(R.string.donations__alert_dialog_no_browser));
				}

				return false;
			}

			/**
			 * Links in the flattr iframe should load in the browser not in the
			 * iframe itself, http:/
			 * /stackoverflow.com/questions/5641626/how-to-
			 * get-webview-iframe-link-to-launch-the -browser
			 */
			@Override
			public void onLoadResource(final WebView view, final String url) {
				if (url.contains("flattr")) {
					final HitTestResult result = view.getHitTestResult();
					if (result != null && result.getType() > 0) {
						try {
							view.getContext().startActivity(
									new Intent(Intent.ACTION_VIEW, Uri
											.parse(url)));
						} catch (final ActivityNotFoundException e) {
							openDialog(
									android.R.drawable.ic_dialog_alert,
									R.string.donations__alert_dialog_title,
									getString(R.string.donations__alert_dialog_no_browser));
						}
						view.stopLoading();
					}
				}
			}

			/**
			 * After loading is done, remove frame with progress circle
			 */
			@Override
			public void onPageFinished(final WebView view, final String url) {
				// remove loading frame, show webview
				if (mLoadingFrame.getVisibility() == View.VISIBLE) {
					mLoadingFrame.setVisibility(View.GONE);
					mFlattrWebview.setVisibility(View.VISIBLE);
				}
			}
		});

		// get flattr values from xml config
		final String projectUrl = this.mFlattrProjectUrl;
		final String flattrUrl = this.mFlattrUrl;

		// make text white and background transparent
		final String htmlStart = "<html> <head><style type='text/css'>*{color: #FFFFFF; background-color: transparent;}</style>";

		// https is not working in android 2.1 and 2.2
		String flattrScheme;
		if (Build.VERSION.SDK_INT >= 9) {
			flattrScheme = "https://";
		} else {
			flattrScheme = "http://";
		}

		// set url of flattr link
		this.mFlattrUrlTextView = (TextView) getActivity().findViewById(
				R.id.donations__flattr_url);
		this.mFlattrUrlTextView.setText(flattrScheme + flattrUrl);

		final String flattrJavascript = "<script type='text/javascript'>"
				+ "/* <![CDATA[ */"
				+ "(function() {"
				+ "var s = document.createElement('script'), t = document.getElementsByTagName('script')[0];"
				+ "s.type = 'text/javascript';" + "s.async = true;"
				+ "s.src = '" + flattrScheme
				+ "api.flattr.com/js/0.6/load.js?mode=auto';"
				+ "t.parentNode.insertBefore(s, t);" + "})();" + "/* ]]> */"
				+ "</script>";
		final String htmlMiddle = "</head> <body> <div align='center'>";
		final String flattrHtml = "<a class='FlattrButton' style='display:none;' href='"
				+ projectUrl
				+ "' target='_blank'></a> <noscript><a href='"
				+ flattrScheme
				+ flattrUrl
				+ "' target='_blank'> <img src='"
				+ flattrScheme
				+ "api.flattr.com/button/flattr-badge-large.png' alt='Flattr this' title='Flattr this' border='0' /></a></noscript>";
		final String htmlEnd = "</div> </body> </html>";

		final String flattrCode = htmlStart + flattrJavascript + htmlMiddle
				+ flattrHtml + htmlEnd;

		mFlattrWebview.getSettings().setJavaScriptEnabled(true);

		mFlattrWebview.loadData(flattrCode, "text/html", "utf-8");

		// disable scroll on touch
		mFlattrWebview.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(final View view,
					final MotionEvent motionEvent) {
				// already handled (returns true) when moving
				return motionEvent.getAction() == MotionEvent.ACTION_MOVE;
			}
		});

		// make background of webview transparent
		// has to be called AFTER loadData
		// http://stackoverflow.com/questions/5003156/android-webview-style-background-colortransparent-ignored-on-android-2-2
		mFlattrWebview.setBackgroundColor(0x00000000);
	}
}
