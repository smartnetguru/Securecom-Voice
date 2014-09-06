/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2014 Securecom
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.securecomcode.voice.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.directory.DirectoryUpdateReceiver;
import com.securecomcode.voice.gcm.GCMRegistrarHelper;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.ActionBar.TabListener;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.securecomcode.voice.monitor.MonitorConfigUpdateReceiver;
import com.securecomcode.voice.util.PeriodicActionUtils;

/**
 * The base dialer activity.  A tab container for the contacts, call log, and favorites tab.
 *
 * @author Moxie Marlinspike
 *
 */

public class DialerActivity extends SherlockFragmentActivity {

  public static final int    MISSED_CALL     = 1;
  public static final String CALL_LOG_ACTION = "com.securecomcode.voice.ui.DialerActivity";

  private static final int CALL_LOG_TAB_INDEX = 1;

  private ViewPager viewPager;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    ActionBar actionBar = this.getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    actionBar.setDisplayShowHomeEnabled(false);
    actionBar.setDisplayShowTitleEnabled(false);
    actionBar.setDisplayUseLogoEnabled(false);

    checkForFreshInstall();
    setContentView(R.layout.dialer_activity);

    setupViewPager();
    setupTabs();

    GCMRegistrarHelper.registerClient(this, false);
  }

  @Override
  public void onNewIntent(Intent intent) {
    setIntent(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (getIntent().getAction() != null &&
        getIntent().getAction().equals(CALL_LOG_ACTION))
    {
      getIntent().setAction(null);
      getSupportActionBar().setSelectedNavigationItem(CALL_LOG_TAB_INDEX);
    }
  }

  private void setupViewPager() {
    viewPager = (ViewPager) findViewById(R.id.pager);
    viewPager.setAdapter(new DialerPagerAdapter(getSupportFragmentManager()));
    viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int i, float v, int i2) {
      }

      @Override
      public void onPageSelected(int i) {
        getSupportActionBar().setSelectedNavigationItem(i);
      }

      @Override
      public void onPageScrollStateChanged(int i) {
      }
    });
  }

  private void setupTabs() {
    int[] icons = new int[] { R.drawable.ic_tab_contacts, R.drawable.ic_tab_recent, R.drawable.ic_tab_favorites, R.drawable.ic_menu_dialpad_dk};

    for (int i = 0; i < icons.length; i++) {
      ActionBar.Tab tab = getSupportActionBar().newTab();
      tab.setIcon(icons[i]);

      final int tabIndex = i;
      tab.setTabListener(new TabListener() {
        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
          viewPager.setCurrentItem(tabIndex);
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
      });
      getSupportActionBar().addTab(tab);
    }
  }

  private void checkForFreshInstall() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (preferences.getBoolean(Constants.VERIFYING_PREFERENCE, false)) {
      Log.w("DialerActivity", "Verification underway...");
      startActivity(new Intent(this, RegistrationProgressActivity.class));
      finish();
    }

    if (!preferences.getBoolean(Constants.REGISTERED_PREFERENCE, false)) {
      Log.w("DialerActivity", "Not registered and not verifying...");
      startActivity(new Intent(this, CreateAccountActivity.class));
      finish();
    }

    PeriodicActionUtils.scheduleUpdate(this, DirectoryUpdateReceiver.class);
    PeriodicActionUtils.scheduleUpdate(this, MonitorConfigUpdateReceiver.class);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.dialer_options_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.resetPasswordItem: launchResetPasswordActivity();  return true;
    case R.id.aboutItem:         launchAboutActivity();          return true;
    case R.id.settingsItem:      launchPreferencesActivity();    return true;
    }
    return false;
  }

  private void launchPreferencesActivity() {
    startActivity(new Intent(this, ApplicationPreferencesActivity.class));
  }

  private void launchResetPasswordActivity() {
    startActivity(new Intent(this, CreateAccountActivity.class));
    finish();
  }

  private void launchAboutActivity() {
    startActivity(new Intent(this, AboutActivity.class));
  }

  private static class DialerPagerAdapter extends FragmentPagerAdapter {

    public DialerPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int i) {
      switch (i) {
        case 0:
          return new ContactsListActivity();
        case 1:
          return new RecentCallListActivity();
        case 3:
              return new DialpadFragment();
        case 2:
        default:
          ContactsListActivity fragment = new ContactsListActivity();
          Bundle args = new Bundle();
          args.putBoolean("favorites", true);
          fragment.setArguments(args);
          return fragment;
      }
    }

    @Override
    public int getCount() {
      return 4;
    }

  }

}
