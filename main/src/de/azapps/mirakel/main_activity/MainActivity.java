/*******************************************************************************
 * Mirakel is an Android App for managing your ToDo-Lists
 * 
 * Copyright (c) 2013-2014 Anatolij Zelenin, Georg Semmler.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.azapps.mirakel.main_activity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.Vector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.fourmob.datetimepicker.date.DatePicker;
import com.fourmob.datetimepicker.date.DatePickerDialog;

import de.azapps.changelog.Changelog;
import de.azapps.ilovefs.ILoveFS;
import de.azapps.mirakel.DefinitionsHelper;
import de.azapps.mirakel.DefinitionsHelper.NoSuchListException;
import de.azapps.mirakel.adapter.PagerAdapter;
import de.azapps.mirakel.custom_views.TaskSummary;
import de.azapps.mirakel.helper.BuildHelper;
import de.azapps.mirakel.helper.Helpers;
import de.azapps.mirakel.helper.ListDialogHelpers;
import de.azapps.mirakel.helper.MirakelCommonPreferences;
import de.azapps.mirakel.helper.MirakelModelPreferences;
import de.azapps.mirakel.helper.SharingHelper;
import de.azapps.mirakel.helper.TaskDialogHelpers;
import de.azapps.mirakel.helper.TaskHelper;
import de.azapps.mirakel.helper.UndoHistory;
import de.azapps.mirakel.main_activity.list_fragment.ListFragment;
import de.azapps.mirakel.main_activity.task_fragment.TaskFragment;
import de.azapps.mirakel.main_activity.task_fragment.TaskFragmentV14;
import de.azapps.mirakel.main_activity.task_fragment.TaskFragmentV8;
import de.azapps.mirakel.main_activity.tasks_fragment.TasksFragment;
import de.azapps.mirakel.model.account.AccountMirakel;
import de.azapps.mirakel.model.account.AccountMirakel.ACCOUNT_TYPES;
import de.azapps.mirakel.model.file.FileMirakel;
import de.azapps.mirakel.model.list.ListMirakel;
import de.azapps.mirakel.model.list.SpecialList;
import de.azapps.mirakel.model.semantic.Semantic;
import de.azapps.mirakel.model.task.Task;
import de.azapps.mirakel.reminders.ReminderAlarm;
import de.azapps.mirakel.services.NotificationService;
import de.azapps.mirakel.static_activities.DonationsActivity;
import de.azapps.mirakel.static_activities.SettingsActivity;
import de.azapps.mirakel.static_activities.SplashScreenActivity;
import de.azapps.mirakel.widget.MainWidgetProvider;
import de.azapps.mirakelandroid.R;
import de.azapps.tools.FileUtils;
import de.azapps.tools.Log;

/**
 * This is our main activity. Here happens nearly everything.
 * 
 * @author az
 */
public class MainActivity extends ActionBarActivity implements
		ViewPager.OnPageChangeListener {
	private static boolean isRTL;
	// Intent variables
	public static final int LEFT_FRAGMENT = 0, RIGHT_FRAGMENT = 1;
	public static final int RESULT_SPEECH_NAME = 1, RESULT_SPEECH = 3,
			RESULT_SETTINGS = 4, RESULT_ADD_FILE = 5, RESULT_CAMERA = 6,
			RESULT_ADD_PICTURE = 7;
	private static final String TAG = "MainActivity";
	// TODO We should do this somehow else
	public static boolean updateTasksUUID = false;

	protected static int getTaskFragmentPosition() {
		if (MainActivity.isRTL || MirakelCommonPreferences.isTablet()) {
			return MainActivity.LEFT_FRAGMENT;
		}
		return MainActivity.RIGHT_FRAGMENT;
	}

	public static int getTasksFragmentPosition() {
		if (MainActivity.isRTL && !MirakelCommonPreferences.isTablet()) {
			return MainActivity.RIGHT_FRAGMENT;
		}
		return MainActivity.LEFT_FRAGMENT;
	}

	private boolean closeOnBack = false;
	protected ListMirakel currentList;

	protected int currentPosition;
	// State variables
	protected Task currentTask;
	public boolean darkTheme;
	private Uri fileUri;

	private final FragmentManager fragmentManager = getSupportFragmentManager();
	private final Stack<Task> goBackTo = new Stack<Task>();
	// Foo variables (move them out of the MainActivity)
	// User interaction variables
	private boolean isResumed;

	private List<ListMirakel> lists;
	protected DrawerLayout mDrawerLayout;
	private ActionBarDrawerToggle mDrawerToggle;
	protected Menu menu;
	private PagerAdapter mPagerAdapter;

	protected MainActivityBroadcastReceiver mSyncReciver;

	// Layout variables
	ViewPager mViewPager;

	private String newTaskContent, newTaskSubject;

	private View oldClickedList = null;

	protected TaskSummary oldClickedTask = null;

	private boolean showNavDrawer = false;

	private boolean skipSwipe;
	private Intent startIntent;
	private int previousState;

	private void addFilesForTask(final Task t, final Intent intent) {
		final String action = intent.getAction();
		final String type = intent.getType();
		this.currentPosition = getTaskFragmentPosition();

		if (Intent.ACTION_SEND.equals(action) && type != null) {
			final Uri uri = (Uri) intent
					.getParcelableExtra(Intent.EXTRA_STREAM);
			t.addFile(this, FileUtils.getPathFromUri(uri, this));
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
			final ArrayList<Uri> imageUris = intent
					.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
			for (final Uri uri : imageUris) {
				t.addFile(this, FileUtils.getPathFromUri(uri, this));
			}
		}

	}

	private void addTaskFromSharing(final ListMirakel list) {
		if (this.newTaskSubject == null) {
			return;
		}
		this.skipSwipe = true;
		final Task task = Semantic.createTask(this.newTaskSubject, list, true,
				this);
		task.setContent(this.newTaskContent == null ? "" : this.newTaskContent);
		task.safeSave();
		setCurrentTask(task);
		if (this.startIntent != null) {
			addFilesForTask(task, this.startIntent);
		}
		setCurrentList(task.getList());
		this.skipSwipe = true;
		setCurrentTask(task, true);
	}

	/**
	 * Clear all highlighted tasks
	 */
	private void clearAllHighlights() {
		if (this.oldClickedList != null) {
			this.oldClickedList.setSelected(false);
			this.oldClickedList.setBackgroundColor(0x00000000);
		}
		if (this.oldClickedTask != null) {
			this.oldClickedTask.setSelected(false);
			this.oldClickedTask.updateBackground();
		}
		clearHighlighted();
	}

	/**
	 * Clear the old highlighted task
	 */
	protected void clearHighlighted() {
		if (this.oldClickedTask == null) {
			return;
		}
		try {
			final ListView view = (ListView) getTasksFragment()
					.getFragmentView().findViewById(R.id.tasks_list);
			final int pos_old = view.getPositionForView(this.oldClickedTask);
			if (pos_old != -1) {
				((TaskSummary) view.getChildAt(pos_old)).updateBackground();
			} else {
				Log.wtf(MainActivity.TAG, "View not found");
			}
		} catch (final Exception e) {
			Log.wtf(MainActivity.TAG, "Listview not found");
			Log.e(MainActivity.TAG, Log.getStackTraceString(e));
		}
	}

	private void forceRebuildLayout() {
		this.mPagerAdapter = null;
		this.isResumed = false;
		this.skipSwipe = true;
		setupLayout();
		this.skipSwipe = true;
		if (getTaskFragment() != null) {
			getTaskFragment().update(MainActivity.this.currentTask);
		}
		loadMenu(this.currentPosition, false, false);
	}

	/**
	 * Return the currently showed List
	 * 
	 * @return
	 */
	public ListMirakel getCurrentList() {
		if (this.currentList == null) {
			this.currentList = SpecialList.firstSpecialSafe(this);
		}
		return this.currentList;
	}

	public void setCurrentList(final ListMirakel currentList) {
		setCurrentList(currentList, null, true, true);
	}

	/**
	 * Set the current list and update the views
	 * 
	 * @param currentList
	 * @param switchFragment
	 */
	public void setCurrentList(final ListMirakel currentList,
			final boolean switchFragment) {
		setCurrentList(currentList, null, switchFragment, true);
	}

	public void setCurrentList(final ListMirakel currentList,
			final View currentView) {
		setCurrentList(currentList, currentView, true, true);
	}

	public void setCurrentList(final ListMirakel currentList,
			final View currentView, final boolean switchFragment,
			final boolean resetGoBackTo) {
		if (currentList == null) {
			return;
		}
		if (resetGoBackTo) {
			this.goBackTo.clear();
		}
		this.currentList = currentList;
		if (this.mDrawerLayout != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					MainActivity.this.mDrawerLayout.closeDrawers();
				}
			});
		}

		this.currentTask = currentList.getFirstTask();
		if (this.currentTask == null) {
			this.currentTask = Task.getDummy(getApplicationContext());
		}

		if (getTasksFragment() != null) {
			getTasksFragment().updateList(true);
			if (switchFragment) {
				setCurrentItem(MainActivity.getTasksFragmentPosition());
			}
		}
		final View currentViewL = getCurrentView(currentView);

		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (currentViewL != null
						&& MirakelCommonPreferences.highlightSelected()) {
					clearHighlighted();
					if (MainActivity.this.oldClickedList != null) {
						MainActivity.this.oldClickedList.setSelected(false);
						MainActivity.this.oldClickedList
								.setBackgroundColor(0x00000000);
					}
					currentViewL.setBackgroundColor(getResources().getColor(
							R.color.pressed_color));
					MainActivity.this.oldClickedList = currentViewL;
				}
			}
		});
		if (switchFragment) {
			setCurrentTask(this.currentTask);
		}
		if (this.currentPosition == getTasksFragmentPosition()) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					getSupportActionBar().setTitle(
							MainActivity.this.currentList.getName());
				}

			});
		}

	}

	/**
	 * Return the currently showed tasks
	 * 
	 * @return
	 */
	public Task getCurrentTask() {
		this.currentTask = this.currentList.getFirstTask();
		if (this.currentTask == null) {
			this.currentTask = Task.getDummy(getApplicationContext());
		}

		return this.currentTask;
	}

	/**
	 * Set the current task and update the view
	 * 
	 * @param currentTask
	 */
	public void setCurrentTask(final Task currentTask) {
		setCurrentTask(currentTask, false);
	}

	public void setCurrentTask(final Task currentTask,
			final boolean switchFragment) {
		setCurrentTask(currentTask, switchFragment, true);
	}

	public void setCurrentTask(final Task currentTask,
			final boolean switchFragment, final boolean resetGoBackTo) {
		if (currentTask == null) {
			return;
		}
		this.currentTask = currentTask;
		this.skipSwipe = true;
		if (resetGoBackTo) {
			this.goBackTo.clear();
		}

		highlightCurrentTask(currentTask, false);

		if (getTaskFragment() != null) {
			getTaskFragment().update(currentTask);
			Log.d(MainActivity.TAG, "current task");
			if (!MirakelCommonPreferences.isTablet() && switchFragment) {
				setCurrentItem(getTaskFragmentPosition());
			}
		}
	}

	/**
	 * Returns the current position of the viewPager
	 * 
	 * @return
	 */
	public int getCurrentPosition() {
		return this.currentPosition;
	}

	/**
	 * Returns the ListFragment
	 * 
	 * @return
	 */
	public ListFragment getListFragment() {
		return (ListFragment) this.fragmentManager
				.findFragmentById(R.id.navigate_fragment);
	}

	/**
	 * Returns the TaskFragment
	 * 
	 * @return
	 */
	public TaskFragment getTaskFragment() {
		if (MirakelCommonPreferences.isTablet()) {
			return (TaskFragment) this.fragmentManager
					.findFragmentById(R.id.task_fragment);
		} else {
			checkPageAdapter();
			return (TaskFragment) this.mPagerAdapter
					.getItem(getTaskFragmentPosition());
		}
	}

	/**
	 * Returns the TaskFragment
	 * 
	 * @return
	 */
	public TasksFragment getTasksFragment() {
		Fragment f;
		if (MirakelCommonPreferences.isTablet()) {
			f = this.fragmentManager.findFragmentById(R.id.tasks_fragment);
			if (f == null) {
				this.fragmentManager.findFragmentByTag("tasks");
			}
			if (f == null) {
				Log.wtf(MainActivity.TAG, "fragment is null");
			}

		} else {
			checkPageAdapter();
			f = this.mPagerAdapter.getItem(MainActivity
					.getTasksFragmentPosition());
		}
		// This must not happen
		if (f == null && this.mPagerAdapter != null) {
			f = this.mPagerAdapter.getItem(getTasksFragmentPosition());
			if (f == null) {
				Log.wtf(MainActivity.TAG, "no taskfragment found");
				return null;
			}
		}
		try {
			return (TasksFragment) f;
		} catch (final ClassCastException e) {
			Log.wtf(MainActivity.TAG, "cannot cast fragment");
			forceRebuildLayout();
			return getTasksFragment();
		}
	}

	private void checkPageAdapter() {
		if (this.mPagerAdapter == null && !MirakelCommonPreferences.isTablet()) {
			forceRebuildLayout();
			if (this.mPagerAdapter == null) {
				// something terrible happened
				Log.wtf(MainActivity.TAG, "pageadapter after init null");
			}
		}
	}

	/**
	 * Is called if the user want to destroy a List
	 * 
	 * @param lists
	 */
	public void handleDestroyList(final List<ListMirakel> lists) {
		String names = lists.get(0).getName();
		for (int i = 1; i < lists.size(); i++) {
			names += ", " + lists.get(i).getName();
		}
		new AlertDialog.Builder(this)
				.setTitle(
						getResources().getQuantityString(R.plurals.list_delete,
								lists.size()))
				.setMessage(this.getString(R.string.list_delete_content, names))
				.setPositiveButton(this.getString(android.R.string.yes),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog,
									final int which) {
								new Thread(new Runnable() {

									@Override
									public void run() {
										for (final ListMirakel list : lists) {
											list.destroy();
											if (getCurrentList().getId() == list
													.getId()) {
												setCurrentList(SpecialList
														.firstSpecial());
											}
										}
										if (getListFragment() != null) {
											getListFragment().update();
										}

									}

								}).start();

							}
						})
				.setNegativeButton(this.getString(android.R.string.no), null)
				.show();
	}

	/**
	 * Handle the actions after clicking on a destroy-list button
	 * 
	 * @param list
	 */
	public void handleDestroyList(final ListMirakel list) {
		final List<ListMirakel> l = new ArrayList<ListMirakel>();
		l.add(list);
		handleDestroyList(l);
	}

	/**
	 * Is called if the user want to destroy a Task
	 * 
	 * @param lists
	 */
	public void handleDestroyTask(final List<Task> tasks) {
		if (tasks == null) {
			return;
		}
		final MainActivity main = this;
		// This must then be a bug in a ROM
		if (tasks.size() == 0 || tasks.get(0) == null) {
			return;
		}
		String names = tasks.get(0).getName();
		for (int i = 1; i < tasks.size(); i++) {
			names += ", " + tasks.get(i).getName();
		}
		new AlertDialog.Builder(this)
				.setTitle(
						getResources().getQuantityString(R.plurals.task_delete,
								tasks.size()))
				.setMessage(this.getString(R.string.task_delete_content, names))
				.setPositiveButton(this.getString(android.R.string.yes),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog,
									final int which) {
								for (final Task t : tasks) {
									t.destroy();
								}
								setCurrentList(MainActivity.this.currentList);
								ReminderAlarm.updateAlarms(main);
								updateShare();
							}
						})
				.setNegativeButton(this.getString(android.R.string.no), null)
				.show();
		if (getTasksFragment() != null) {
			getTasksFragment().updateList(false);
		}
	}

	/**
	 * Handle the actions after clicking on a destroy-task button
	 * 
	 * @param task
	 */
	public void handleDestroyTask(final Task task) {
		final List<Task> t = new ArrayList<Task>();
		t.add(task);
		handleDestroyTask(t);
	}

	/**
	 * Is called if the user want to move a Task
	 * 
	 * @param lists
	 */
	public void handleMoveTask(final List<Task> tasks) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_move);
		final List<CharSequence> items = new ArrayList<CharSequence>();
		final List<Integer> list_ids = new ArrayList<Integer>();
		int currentItem = 0, i = 0;
		for (final ListMirakel list : this.lists) {
			if (list.getId() > 0) {
				items.add(list.getName());
				if (tasks.get(0).getList().getId() == list.getId()
						&& tasks.size() == 1) {
					currentItem = i;
				}
				list_ids.add(list.getId());
				++i;
			}
		}

		builder.setSingleChoiceItems(
				items.toArray(new CharSequence[items.size()]), currentItem,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog,
							final int item) {
						for (final Task t : tasks) {
							t.setList(ListMirakel.getList(list_ids.get(item)),
									true);
							t.safeSave();
						}
						/*
						 * There are 3 possibilities how to handle the post-move
						 * of a task: 1: update the currentList to the List, the
						 * task was moved to setCurrentList(task.getList()); 2:
						 * update the tasksView but not update the taskView:
						 * getTasksFragment().updateList();
						 * getTasksFragment().update(); 3: Set the currentList
						 * to the old List
						 */
						if (MainActivity.this.currentPosition == getTaskFragmentPosition()) {
							final Task task = tasks.get(0);
							if (task == null) {
								// What the hell?
								Log.wtf(MainActivity.TAG, "Task vanished");
							} else {
								setCurrentList(task.getList());
								setCurrentTask(task, true);
							}
						} else {
							setCurrentList(getCurrentList());
							getListFragment().update();
						}
						if (dialog != null) {
							dialog.dismiss();
						}
					}
				}).show();
	}

	/**
	 * Handle the actions after clicking on a move task button
	 * 
	 * @param tasks
	 */
	public void handleMoveTask(final Task task) {
		final List<Task> t = new ArrayList<Task>();
		t.add(task);
		handleMoveTask(t);
	}

	public void handleSetDue(final List<Task> tasks) {
		final Calendar dueLocal = new GregorianCalendar();
		final DatePickerDialog datePickerDialog = DatePickerDialog.newInstance(
				new DatePicker.OnDateSetListener() {

					@Override
					public void onDateSet(final DatePicker dp, final int year,
							final int month, final int day) {
						final Calendar due = new GregorianCalendar(year, month,
								day);
						for (final Task task : tasks) {
							task.setDue(due);
							saveTask(task);
						}
					}

					@Override
					public void onNoDateSet() {
						for (final Task task : tasks) {
							task.setDue(null);
							saveTask(task);
						}

					}
				}, dueLocal.get(Calendar.YEAR), dueLocal.get(Calendar.MONTH),
				dueLocal.get(Calendar.DAY_OF_MONTH), false,
				MirakelCommonPreferences.isDark(), true);
		datePickerDialog.show(getSupportFragmentManager(), "datepicker");
	}

	private int handleTaskFragmentMenu() {
		if (getSupportActionBar() != null && this.currentTask != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Log.d(MainActivity.TAG, "handle menu");
					getTaskFragment().update(MainActivity.this.currentTask);
					if (MainActivity.this.currentTask == null) {
						MainActivity.this.currentTask = Task
								.getDummy(MainActivity.this);
					}
					getSupportActionBar().setTitle(
							MainActivity.this.currentTask.getName());
				}
			});

		}
		return R.menu.activity_task;
	}

	private int handleTasksFragmentMenu() {
		int newmenu;
		getListFragment().enableDrop(false);
		if (this.currentList == null) {
			return -1;
		}
		if (!MirakelCommonPreferences.isTablet()) {
			newmenu = R.menu.tasks;
		} else {
			newmenu = R.menu.tablet_right;
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				getSupportActionBar().setTitle(
						MainActivity.this.currentList.getName());
			}
		});

		return newmenu;
	}

	/**
	 * Highlights the given Task
	 * 
	 * @param currentTask
	 * @param multiselect
	 */
	void highlightCurrentTask(final Task currentTask, final boolean multiselect) {
		if (getTaskFragment() == null || getTasksFragment() == null
				|| getTasksFragment().getAdapter() == null
				|| currentTask == null) {
			return;
		}
		Log.v(MainActivity.TAG, currentTask.getName());
		final View tmpView = getTasksFragment().getViewForTask(currentTask);
		final View currentView = tmpView == null ? getTasksFragment()
				.getListView().getChildAt(0) : tmpView;
		if (currentView != null && MirakelCommonPreferences.highlightSelected()
				&& !multiselect) {

			currentView.post(new Runnable() {

				@Override
				public void run() {
					if (MainActivity.this.oldClickedTask != null) {
						MainActivity.this.oldClickedTask.setSelected(false);
						MainActivity.this.oldClickedTask.updateBackground();
					}
					currentView
							.setBackgroundColor(getResources()
									.getColor(
											MainActivity.this.darkTheme ? R.color.highlighted_text_holo_dark
													: R.color.highlighted_text_holo_light));
					MainActivity.this.oldClickedTask = (TaskSummary) currentView;
				}
			});
		}
	}

	/*
	 * Setup NavigationDrawer
	 */
	private void initNavDrawer() {
		this.mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

		this.mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
		this.mDrawerLayout, /* DrawerLayout object */
		R.drawable.ic_drawer, /* nav drawer icon to replace 'Up' caret */
		R.string.list_title, /* "open drawer" description */
		R.string.list_title /* "close drawer" description */
		) {
			@Override
			public void onDrawerClosed(final View view) {
				loadMenu(MainActivity.this.currentPosition);
				final ListFragment listFragment = getListFragment();
				if (listFragment != null) {
					listFragment.closeNavDrawer();
					listFragment.setActivity(MainActivity.this);
				}

			}

			@Override
			public void onDrawerOpened(final View drawerView) {
				loadMenu(-1, false, false);
				getListFragment().refresh();
			}
		};

		// Set the drawer toggle as the DrawerListener
		this.mDrawerLayout.setDrawerListener(this.mDrawerToggle);
		if (this.showNavDrawer) {
			this.mDrawerLayout.openDrawer(DefinitionsHelper.GRAVITY_LEFT);
		}
	}

	/**
	 * Initialize ViewPager
	 */
	@SuppressLint("NewApi")
	private void initViewPager() {
		final List<Fragment> fragments = new Vector<Fragment>();
		final TasksFragment tasksFragment = new TasksFragment();
		tasksFragment.setActivity(this);
		fragments.add(tasksFragment);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			fragments.add(new TaskFragmentV8());
		} else {
			fragments.add(new TaskFragmentV14());
		}
		if (!MirakelCommonPreferences.isTablet() && !this.isResumed
				&& this.mPagerAdapter == null) {
			if (MainActivity.isRTL) {
				Collections.reverse(fragments);
			}
			this.mPagerAdapter = new PagerAdapter(this.fragmentManager,
					fragments);

			// if (this.mViewPager == null) {
			this.mViewPager = (ViewPager) super.findViewById(R.id.viewpager);
			// }
			if (this.mViewPager == null) {
				Log.wtf(MainActivity.TAG, "viewpager null");
				return;
			}
			this.mViewPager.setOffscreenPageLimit(2);
			this.mViewPager.setAdapter(this.mPagerAdapter);
			this.mViewPager.setOnPageChangeListener(this);
		} else if (this.fragmentManager != null
				&& findViewById(R.id.tasks_fragment) != null
				&& findViewById(R.id.task_fragment) != null) {
			// add fragment to the fragment container layout

			this.fragmentManager.beginTransaction()
					.add(R.id.tasks_fragment, fragments.get(0), "tasks")
					.add(R.id.task_fragment, fragments.get(1), "task")
					.commitAllowingStateLoss();

		}
		if (this.fragmentManager != null
				&& findViewById(R.id.navigate_fragment) != null) {
			final ListFragment listFragment = new ListFragment();
			// add fragment to the fragment container layout
			this.fragmentManager.beginTransaction()
					.add(R.id.navigate_fragment, listFragment).commit();
		}

	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY",
				"WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}

	public void loadMenu(final int position) {
		loadMenu(position, true, false);
	}

	/**
	 * Initializes the menu of the ActionBar for the given position
	 * 
	 * @param position
	 * @param setPosition
	 * @param fromShare
	 */
	public void loadMenu(final int position, final boolean setPosition,
			final boolean fromShare) {
		if (getTaskFragment() != null && getTaskFragment().getView() != null) {
			final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(getTaskFragment().getView()
					.getWindowToken(), 0);
		}
		if (this.menu == null) {
			return;
		}
		final int newmenu;
		if (MirakelCommonPreferences.isTablet() && position != -1) {
			newmenu = R.menu.tablet_right;
		} else {
			switch (position) {
			case -1:
				newmenu = R.menu.activity_list;
				getSupportActionBar().setTitle(getString(R.string.list_title));
				break;
			case RIGHT_FRAGMENT:
				newmenu = MainActivity.isRTL ? handleTasksFragmentMenu()
						: handleTaskFragmentMenu();
				break;
			case LEFT_FRAGMENT:
				newmenu = MainActivity.isRTL ? handleTaskFragmentMenu()
						: handleTasksFragmentMenu();
				break;
			default:
				Toast.makeText(getApplicationContext(),
						"Where are the dragons?", Toast.LENGTH_LONG).show();
				return;
			}
		}
		if (setPosition) {
			this.currentPosition = position;
		}

		// Configure to use the desired menu
		if (newmenu == -1) {
			return;
		}
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				MainActivity.this.menu.clear();
				final MenuInflater inflater = getMenuInflater();
				inflater.inflate(newmenu, MainActivity.this.menu);
				if (MainActivity.this.menu.findItem(R.id.menu_sync_now) != null) {
					MainActivity.this.menu.findItem(R.id.menu_sync_now)
							.setVisible(MirakelModelPreferences.useSync());
				}
				if (MainActivity.this.menu.findItem(R.id.menu_kill_button) != null) {
					MainActivity.this.menu.findItem(R.id.menu_kill_button)
							.setVisible(
									MirakelCommonPreferences.showKillButton());
				}
				if (MainActivity.this.menu.findItem(R.id.menu_contact) != null) {
					MainActivity.this.menu.findItem(R.id.menu_contact)
							.setVisible(BuildHelper.isBeta());
				}

				if (!fromShare) {
					updateShare();
				}

			}
		});
	}

	/**
	 * Locks the drawer so the user cannot open or close it
	 */
	public void lockDrawer() {
		this.mDrawerLayout
				.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
	}

	/**
	 * Unlocks the drawer
	 */
	public void unlockDrawer() {
		this.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent intent) {
		final boolean isOk = resultCode == Activity.RESULT_OK;
		Log.v(MainActivity.TAG, "Result:" + requestCode);
		switch (requestCode) {
		case RESULT_SPEECH_NAME:
			if (intent != null) {
				final ArrayList<String> text = intent
						.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				((EditText) findViewById(R.id.edit_name)).setText(text.get(0));
			}
			break;
		case RESULT_SPEECH:
			if (intent != null) {
				final ArrayList<String> text = intent
						.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				((EditText) getTasksFragment().getFragmentView().findViewById(
						R.id.tasks_new)).setText(text.get(0));
			}
			break;
		case RESULT_ADD_FILE:
			if (intent != null) {
				Log.d(MainActivity.TAG,
						"taskname " + this.currentTask.getName());
				final String file_path = FileUtils.getPathFromUri(
						intent.getData(), this);
				if (FileMirakel.newFile(this, this.currentTask, file_path) == null) {
					Toast.makeText(this, getString(R.string.file_vanished),
							Toast.LENGTH_SHORT).show();
				} else {
					getTaskFragment().update(this.currentTask);
				}
			}
			break;
		case RESULT_SETTINGS:
			if (getListFragment() != null) {
				getListFragment().update();
			} else {
				forceRebuildLayout();
			}
			if (getTasksFragment() != null) {
				getTaskFragment().updateLayout();
			} else {
				forceRebuildLayout();
			}
			if (!MirakelCommonPreferences.highlightSelected()
					&& (this.oldClickedList != null || this.oldClickedTask == null)) {
				clearAllHighlights();
			}
			if (this.darkTheme != MirakelCommonPreferences.isDark()) {
				finish();
				if (this.startIntent == null) {
					this.startIntent = new Intent(MainActivity.this,
							MainActivity.class);
					this.startIntent.setAction(DefinitionsHelper.SHOW_LISTS);
					Log.wtf(MainActivity.TAG,
							"startIntent is null by switching theme");

				}
				startActivity(this.startIntent);
			}
			if (MirakelCommonPreferences.isTablet()) {
				forceRebuildLayout();
			} else if (this.mViewPager != null) {
				loadMenu(this.mViewPager.getCurrentItem());
			}
			if (getTasksFragment() != null) {
				getTasksFragment().updateButtons();
			}
			if (getTaskFragment() != null) {
				getTaskFragment().update(this.currentTask);
			}
			return;
		case RESULT_CAMERA:
		case RESULT_ADD_PICTURE:
			if (isOk) {
				Task task;
				if (requestCode == MainActivity.RESULT_ADD_PICTURE) {
					task = this.currentTask;
				} else {
					task = Semantic.createTask(
							MirakelCommonPreferences.getPhotoDefaultTitle(),
							this.currentList, false, this);
					task.safeSave();
				}
				task.addFile(this, FileUtils.getPathFromUri(this.fileUri, this));
				getTaskFragment().update(task);
			}
			break;
		default:
			Log.w(MainActivity.TAG, "unknown activity result");
			break;
		}
	}

	@Override
	public void onBackPressed() {
		if (this.goBackTo.size() > 0
				&& this.currentPosition == getTaskFragmentPosition()) {
			final Task goBack = this.goBackTo.pop();
			setCurrentList(goBack.getList(), null, false, false);
			setCurrentTask(goBack, false, false);
			return;
		}
		if (this.closeOnBack) {
			super.onBackPressed();
			return;
		}
		if (!MirakelCommonPreferences.isTablet()) {
			switch (this.mViewPager.getCurrentItem()) {
			case LEFT_FRAGMENT:
				if (MainActivity.isRTL) {
					this.mViewPager.setCurrentItem(MainActivity
							.getTasksFragmentPosition());
					return;
				}
				break;
			case RIGHT_FRAGMENT:
				if (!MainActivity.isRTL) {
					this.mViewPager.setCurrentItem(MainActivity
							.getTasksFragmentPosition());
					return;
				}
				break;
			default:
				// Cannot be, do nothing
				break;
			}
		}
		super.onBackPressed();
	}

	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
		Locale.setDefault(Helpers.getLocal(this));
		super.onConfigurationChanged(newConfig);
		this.mPagerAdapter = null;
		this.isResumed = false;
		draw();
		if (getListFragment() != null && getTasksFragment() != null
				&& this.mDrawerToggle != null) {
			getListFragment().setActivity(this);
			getTasksFragment().setActivity(this);
			this.mDrawerToggle.onConfigurationChanged(newConfig);
			getTasksFragment().hideActionMode();
			getTaskFragment().closeActionMode();
			getListFragment().hideActionMode();
		}
	}

	/**
	 * To execute before super.onCreate()
	 */
	private void initFirst() {
		this.darkTheme = MirakelCommonPreferences.isDark();
		if (this.darkTheme) {
			setTheme(android.support.v7.appcompat.R.style.Theme_AppCompat);
		} else {
			setTheme(android.support.v7.appcompat.R.style.Theme_AppCompat_Light_DarkActionBar);
		}
	}

	/**
	 * Loads the settings
	 */
	@SuppressLint("NewApi")
	private void initConfiguration() {
		Locale.setDefault(Helpers.getLocal(this));
		MainActivity.isRTL = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
				&& getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
		this.currentPosition = MainActivity.getTasksFragmentPosition();
		this.mPagerAdapter = null;
		this.skipSwipe = false;
		Log.d(MainActivity.TAG, "false");
		this.startIntent = getIntent();
		this.closeOnBack = false;
	}

	/**
	 * Initialize and load third party stuff
	 */
	private void initThirdParty() {
		// Show ChangeLog
		final Changelog cl = new Changelog(this);
		cl.showChangelog();
		final ILoveFS ilfs = new ILoveFS(this, "mirakel@azapps.de",
				DefinitionsHelper.APK_NAME);
		if (ilfs.isILFSDay()) {
			ilfs.donateListener = new DialogInterface.OnClickListener() {

				@Override
				public void onClick(final DialogInterface dialog,
						final int which) {
					final Intent intent = new Intent(MainActivity.this,
							DonationsActivity.class);
					startActivity(intent);
				}
			};
			ilfs.show();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		initFirst();
		super.onCreate(savedInstanceState);
		BackgroundTasks.run(this);
		initThirdParty();
		draw();
	}

	private void draw() {
		((ViewGroup) findViewById(android.R.id.content)).removeAllViews();
		if (MirakelCommonPreferences.isTablet()) {
			setContentView(R.layout.pane_multi);
		} else {
			setContentView(R.layout.pane_single);
		}
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		setupLayout();
		this.isResumed = false;
		setCurrentItem(this.currentPosition);
		loadMenu(this.currentPosition);
	}

	private void setCurrentItem(final int pos) {
		if (!MirakelCommonPreferences.isTablet()
				&& this.mViewPager.getCurrentItem() != pos) {
			this.skipSwipe = true;
			this.mViewPager.postDelayed(new Runnable() {
				@Override
				public void run() {
					MainActivity.this.skipSwipe = true;
					MainActivity.this.mViewPager.setCurrentItem(pos);
				}
			}, 10);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		updateLists();
		getMenuInflater().inflate(R.menu.main, menu);
		this.menu = menu;
		if (!this.showNavDrawer) {
			loadMenu(this.currentPosition, false, false);
		} else {
			this.showNavDrawer = false;
			loadMenu(-1, false, false);
		}
		return true;
	}

	@Override
	protected void onDestroy() {
		try {
			BackgroundTasks.onDestroy(this);
		} catch (final Exception e) {
			// eat it
		}
		super.onDestroy();
	}

	// TODO Fix Intent-behavior
	// default is not return new Intent by calling getIntent
	@Override
	protected void onNewIntent(final Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		this.startIntent = intent;
		handleIntent();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (this.mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		switch (item.getItemId()) {
		case R.id.menu_delete:
			handleDestroyTask(this.currentTask);
			updateShare();
			return true;
		case R.id.menu_move:
			handleMoveTask(this.currentTask);
			return true;
		case R.id.list_delete:
			handleDestroyList(this.currentList);
			return true;
		case R.id.task_sorting:
			this.currentList = ListDialogHelpers.handleSortBy(this,
					this.currentList, new Helpers.ExecInterface() {

						@Override
						public void exec() {
							setCurrentList(MainActivity.this.currentList);
						}
					}, null);
			return true;
		case R.id.menu_new_list:
			getListFragment().editList(null);
			return true;
		case R.id.menu_sort_lists:
			final boolean t = !item.isChecked();
			getListFragment().enableDrop(t);
			item.setChecked(t);
			return true;
		case R.id.menu_settings:
			final Intent intent = new Intent(MainActivity.this,
					SettingsActivity.class);
			startActivityForResult(intent, MainActivity.RESULT_SETTINGS);
			break;
		case R.id.menu_contact:
			Helpers.contact(this);
			break;
		case R.id.menu_sync_now:
			final Bundle bundle = new Bundle();
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

			new Thread(new Runnable() {
				@SuppressLint("InlinedApi")
				@Override
				public void run() {
					final List<AccountMirakel> accounts = AccountMirakel
							.getEnabled(true);
					for (final AccountMirakel a : accounts) {
						// davdroid accounts should be there only from
						// API>=14...
						ContentResolver.requestSync(
								a.getAndroidAccount(),
								a.getType() == ACCOUNT_TYPES.TASKWARRIOR ? DefinitionsHelper.AUTHORITY_TYP
										: CalendarContract.AUTHORITY, bundle);
					}

				}
			}).start();
			break;
		case R.id.share_task:
			SharingHelper.share(this, getCurrentTask());
			break;
		case R.id.share_list:
			SharingHelper.share(this, getCurrentList());
			break;
		case R.id.search:
			onSearchRequested();
			break;
		case R.id.menu_kill_button:
			// Only Close
			final Intent killIntent = new Intent(getApplicationContext(),
					SplashScreenActivity.class);
			killIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			killIntent.setAction(SplashScreenActivity.EXIT);
			startActivity(killIntent);
			finish();
			return false;
		case R.id.menu_undo:
			UndoHistory.undoLast();
			updateCurrentListAndTask();
			if (this.currentPosition == getTaskFragmentPosition()) {
				setCurrentTask(this.currentTask);
			} else if (getListFragment() != null && getTasksFragment() != null
					&& getListFragment().getAdapter() != null
					&& getTasksFragment().getAdapter() != null) {
				getListFragment().getAdapter().changeData(ListMirakel.all());
				getListFragment().getAdapter().notifyDataSetChanged();
				getTasksFragment().getAdapter().notifyDataSetChanged();
				if (!MirakelCommonPreferences.isTablet()
						&& this.currentPosition == MainActivity
								.getTasksFragmentPosition()) {
					setCurrentList(getCurrentList());
				}
			}
			ReminderAlarm.updateAlarms(this);
			break;
		case R.id.mark_as_subtask:
			TaskDialogHelpers.handleSubtask(this, this.currentTask, null, true);
			break;
		case R.id.menu_task_clone:
			try {
				final Task newTask = this.currentTask.create();
				setCurrentTask(newTask, true);
				getListFragment().update();
				updatesForTask(newTask);
			} catch (final NoSuchListException e) {
				Log.wtf(MainActivity.TAG, "List vanished on task cloning");
			}
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	public void onPageScrolled(final int position, final float positionOffset,
			final int positionOffsetPixels) {
		if (getTaskFragment() != null && getTasksFragment() != null
				&& getTasksFragment().getAdapter() != null
				&& MirakelCommonPreferences.swipeBehavior() && !this.skipSwipe) {
			this.skipSwipe = true;
			if (getTasksFragment() != null) {
				setCurrentTask(getTasksFragment().getLastTouched(), false);
			}
		}
	}

	@Override
	public void onPageScrollStateChanged(final int state) {
		this.skipSwipe = true;
		if (this.previousState == ViewPager.SCROLL_STATE_DRAGGING
				&& state == ViewPager.SCROLL_STATE_SETTLING) {
			this.skipSwipe = false;
		}
		this.previousState = state;
	}

	@Override
	public void onPageSelected(final int position) {
		if (getTasksFragment() != null) {
			getTasksFragment().closeActionMode();
		}
		if (getTaskFragment() != null) {
			getTaskFragment().closeActionMode();
		}
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (MirakelCommonPreferences.lockDrawerInTaskFragment()
						&& position == getTaskFragmentPosition()) {
					MainActivity.this.mDrawerLayout
							.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
				} else {
					MainActivity.this.mDrawerLayout
							.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
				}
			}
		});
		loadMenu(position);

	}

	@SuppressLint("NewApi")
	@Override
	protected void onPause() {
		if (getTasksFragment() != null) {
			getTasksFragment().clearFocus();
		}
		final Intent intent = new Intent(this, MainWidgetProvider.class);
		intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
		// Use an array and EXTRA_APPWIDGET_IDS instead of
		// AppWidgetManager.EXTRA_APPWIDGET_ID,
		// since it seems the onUpdate() is only fired on that:
		final Context context = getApplicationContext();
		final ComponentName name = new ComponentName(context,
				MainWidgetProvider.class);
		final int widgets[] = AppWidgetManager.getInstance(context)
				.getAppWidgetIds(name);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgets);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			for (final int id : widgets) {
				AppWidgetManager.getInstance(this)
						.notifyAppWidgetViewDataChanged(id,
								R.id.widget_tasks_list);
			}
		}
		sendBroadcast(intent);
		TaskDialogHelpers.stopRecording();
		super.onPause();
	}

	@Override
	protected void onPostCreate(final Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		if (this.mDrawerToggle != null) {
			this.mDrawerToggle.syncState();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (this.isResumed) {
			setupLayout();
		}
		this.isResumed = true;
	}

	/**
	 * Ugly Wrapper TODO make it more beautiful
	 * 
	 * @param task
	 */
	public void saveTask(final Task task) {
		Log.v(MainActivity.TAG, "Saving task… (task:" + task.getId());
		task.safeSave();
		updatesForTask(task);
	}

	private void search(final String query) {
		if (getTasksFragment() != null) {
			getTasksFragment().search(query);
		}
		if (!MirakelCommonPreferences.isTablet()) {
			setCurrentItem(MainActivity.getTasksFragmentPosition());
		}
		getSupportActionBar().setTitle(
				getString(R.string.search_result_title, query));
	}

	private View getCurrentView(final View currentView) {
		if (currentView == null && getListFragment() != null
				&& getListFragment().getAdapter() != null) {
			return getListFragment().getAdapter().getViewForList(
					this.currentList);
		}
		return currentView;
	}

	public void setFileUri(final Uri file) {
		this.fileUri = file;
	}

	/**
	 * Set the Task, to which we switch, if the user press the back-button. It
	 * is reseted, if one of the setCurrent*-functions on called
	 * 
	 * @param t
	 */
	public void setGoBackTo(final Task t) {
		this.goBackTo.push(t);
	}

	public void setSkipSwipe() {
		this.skipSwipe = true;
	}

	private void handleIntent() {
		if (this.startIntent == null || this.startIntent.getAction() == null) {
			Log.d(MainActivity.TAG, "action null");
		} else if (this.startIntent.getAction().equals(
				DefinitionsHelper.SHOW_TASK)
				|| this.startIntent.getAction().equals(
						DefinitionsHelper.SHOW_TASK_FROM_WIDGET)) {
			final Task task = TaskHelper.getTaskFromIntent(this.startIntent);
			if (task != null) {
				this.skipSwipe = true;
				this.currentList = task.getList();
				if (this.mDrawerLayout != null) {
					this.mDrawerLayout.postDelayed(new Runnable() {

						@Override
						public void run() {
							setCurrentTask(task, true);
						}
					}, 10);
				}

				// this.skipSwipe = true;
				// setCurrentItem(getTaskFragmentPosition());
			} else {
				Log.d(MainActivity.TAG, "task null");
			}
			if (this.startIntent.getAction().equals(
					DefinitionsHelper.SHOW_TASK_FROM_WIDGET)) {
				this.closeOnBack = true;
			}
		} else if (this.startIntent.getAction().equals(Intent.ACTION_SEND)
				|| this.startIntent.getAction().equals(
						Intent.ACTION_SEND_MULTIPLE)) {
			this.closeOnBack = true;
			this.newTaskContent = this.startIntent
					.getStringExtra(Intent.EXTRA_TEXT);
			this.newTaskSubject = this.startIntent
					.getStringExtra(Intent.EXTRA_SUBJECT);

			// If from google now, the content is the subject…
			if (this.startIntent.getCategories() != null
					&& this.startIntent.getCategories().contains(
							"com.google.android.voicesearch.SELF_NOTE")) {
				if (!this.newTaskContent.equals("")) {
					this.newTaskSubject = this.newTaskContent;
					this.newTaskContent = "";
				}
			}

			if (!this.startIntent.getType().equals("text/plain")) {
				if (this.newTaskSubject == null) {
					this.newTaskSubject = MirakelCommonPreferences
							.getImportFileTitle();
				}
			}
			final ListMirakel listFromSharing = MirakelModelPreferences
					.getImportDefaultList(false);
			if (listFromSharing != null) {
				addTaskFromSharing(listFromSharing);
			} else {
				final AlertDialog.Builder builder = new AlertDialog.Builder(
						this);
				builder.setTitle(R.string.import_to);
				final List<CharSequence> items = new ArrayList<CharSequence>();
				final List<Integer> list_ids = new ArrayList<Integer>();
				final int currentItem = 0;
				for (final ListMirakel list : ListMirakel.all()) {
					if (list.getId() > 0) {
						items.add(list.getName());
						list_ids.add(list.getId());
					}
				}
				builder.setSingleChoiceItems(
						items.toArray(new CharSequence[items.size()]),
						currentItem, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog,
									final int item) {
								addTaskFromSharing(ListMirakel.getList(list_ids
										.get(item)));
								dialog.dismiss();
							}
						});
				builder.create().show();
			}
		} else if (this.startIntent.getAction().equals(
				DefinitionsHelper.SHOW_LIST)
				|| this.startIntent.getAction().contains(
						DefinitionsHelper.SHOW_LIST_FROM_WIDGET)) {

			int listId;
			if (this.startIntent.getAction()
					.equals(DefinitionsHelper.SHOW_LIST)) {
				listId = this.startIntent.getIntExtra(
						DefinitionsHelper.EXTRA_ID, 0);
			} else {
				listId = Integer.parseInt(this.startIntent.getAction().replace(
						DefinitionsHelper.SHOW_LIST_FROM_WIDGET, ""));
			}
			Log.v(MainActivity.TAG, "ListId: " + listId);
			ListMirakel list = ListMirakel.getList(listId);
			if (list == null) {
				list = SpecialList.firstSpecial();
			}
			setCurrentList(list);
			if (this.startIntent.getAction().contains(
					DefinitionsHelper.SHOW_LIST_FROM_WIDGET)) {
				this.closeOnBack = true;
			}
			this.currentTask = list.getFirstTask();
			if (getTaskFragment() != null) {
				getTaskFragment().update(this.currentTask);
			}
		} else if (this.startIntent.getAction().equals(
				DefinitionsHelper.SHOW_LISTS)) {
			this.mDrawerLayout.openDrawer(DefinitionsHelper.GRAVITY_LEFT);
		} else if (this.startIntent.getAction().equals(Intent.ACTION_SEARCH)) {
			final String query = this.startIntent
					.getStringExtra(SearchManager.QUERY);
			search(query);
		} else if (this.startIntent.getAction().contains(
				DefinitionsHelper.ADD_TASK_FROM_WIDGET)) {
			final int listId = Integer.parseInt(this.startIntent.getAction()
					.replace(DefinitionsHelper.ADD_TASK_FROM_WIDGET, ""));
			setCurrentList(ListMirakel.getList(listId));
			if (getTasksFragment() != null && getTasksFragment().isReady()) {
				getTasksFragment().focusNew(true);
			} else if (!MirakelCommonPreferences.isTablet()) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (getTasksFragment() != null) {
							getTasksFragment().focusNew(true);
						} else {
							Log.wtf(MainActivity.TAG, "Tasksfragment null");
						}
					}
				});
			}
		} else if (this.startIntent.getAction().equals(
				DefinitionsHelper.SHOW_MESSAGE)) {
			final String message = this.startIntent
					.getStringExtra(Intent.EXTRA_TEXT);
			String subject = this.startIntent
					.getStringExtra(Intent.EXTRA_SUBJECT);
			if (message != null) {
				if (subject == null) {
					subject = getString(R.string.message_notification);
				}
				new AlertDialog.Builder(this).setTitle(subject)
						.setMessage(message).show();

			}
		} else {
			setCurrentItem(getTaskFragmentPosition());
		}
		if ((this.startIntent == null || this.startIntent.getAction() == null || !this.startIntent
				.getAction().contains(DefinitionsHelper.ADD_TASK_FROM_WIDGET))
				&& getTasksFragment() != null) {
			getTasksFragment().clearFocus();
		}
		setIntent(null);
		if (this.currentList == null) {
			setCurrentList(SpecialList.firstSpecial());
		}
	}

	/**
	 * Initialize the ViewPager and setup the rest of the layout
	 */
	private void setupLayout() {
		if (this.currentList == null) {
			setCurrentList(SpecialList.firstSpecial());
		}
		// Initialize ViewPager
		/*
		 * TODO We need the try catch because it throws sometimes a
		 * runtimeexception when adding fragments.
		 */
		try {
			initViewPager();
		} catch (final Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		initNavDrawer();
		this.startIntent = getIntent();
		handleIntent();
	}

	private void updateCurrentListAndTask() {
		if (this.currentTask == null && this.currentList == null) {
			return;
		}
		if (this.currentTask != null) {
			this.currentTask = Task.get(this.currentTask.getId());
		} else {
			if (this.currentList != null) {
				final List<Task> currentTasks = this.currentList
						.tasks(MirakelCommonPreferences.showDoneMain());
				if (currentTasks.size() == 0) {
					this.currentTask = Task.getDummy(getApplicationContext());
				} else {
					this.currentTask = currentTasks.get(0);
				}
			}
		}
		if (this.currentList != null) {
			this.currentList = ListMirakel.getList(this.currentList.getId());
		} else {
			this.currentList = this.currentTask.getList();
		}

	}

	/**
	 * Update the internal List of Lists (e.g. for the Move Task dialog)
	 */
	public void updateLists() {
		this.lists = ListMirakel.all(false);
	}

	/**
	 * Executes some View–Updates if a Task was changed
	 * 
	 * @param task
	 */
	public void updatesForTask(final Task task) {
		if (this.currentTask != null
				&& task.getId() == this.currentTask.getId()) {
			this.currentTask = task;
			getTaskFragment().update(task);
		}
		getTasksFragment().updateList(false);
		getListFragment().update();
		NotificationService.updateNotificationAndWidget(this);

	}

	public void updateShare() {
		if (this.menu != null) {
			final MenuItem share_list = this.menu.findItem(R.id.share_list);
			if (share_list != null) {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						if (MainActivity.this.currentList.countTasks() == 0) {
							share_list.setVisible(false);
						} else if (MainActivity.this.currentList.countTasks() > 0) {
							share_list.setVisible(true);
						}

					}
				});

			} else if (this.currentPosition == MainActivity
					.getTasksFragmentPosition()
					&& share_list == null
					&& this.currentList != null
					&& this.currentList.countTasks() > 0
					&& !this.mDrawerLayout
							.isDrawerOpen(DefinitionsHelper.GRAVITY_LEFT)) {
				loadMenu(MainActivity.getTasksFragmentPosition(), true, true);

			}
		}

	}

	public void updateUI() {
		if (getTasksFragment() != null) {
			getTasksFragment().updateList(false);
		}
		if (getTaskFragment() != null && getTaskFragment().getTask() != null) {
			getTaskFragment().update(
					Task.get(getTaskFragment().getTask().getId()));
		}
	}

}
