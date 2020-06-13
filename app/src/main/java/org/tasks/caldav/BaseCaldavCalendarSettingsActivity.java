package org.tasks.caldav;

import static org.tasks.Strings.isNullOrEmpty;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import at.bitfire.dav4jvm.exception.HttpException;
import butterknife.BindView;
import butterknife.OnTextChanged;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.todoroo.astrid.activity.MainActivity;
import com.todoroo.astrid.activity.TaskListFragment;
import com.todoroo.astrid.api.CaldavFilter;
import com.todoroo.astrid.helper.UUIDHelper;
import com.todoroo.astrid.service.TaskDeleter;
import java.net.ConnectException;
import javax.inject.Inject;
import org.tasks.R;
import org.tasks.activities.BaseListSettingsActivity;
import org.tasks.data.CaldavAccount;
import org.tasks.data.CaldavCalendar;
import org.tasks.data.CaldavDao;
import org.tasks.ui.DisplayableException;

public abstract class BaseCaldavCalendarSettingsActivity extends BaseListSettingsActivity {

  public static final String EXTRA_CALDAV_CALENDAR = "extra_caldav_calendar";
  public static final String EXTRA_CALDAV_ACCOUNT = "extra_caldav_account";

  @Inject protected CaldavDao caldavDao;
  @Inject TaskDeleter taskDeleter;

  @BindView(R.id.root_layout)
  LinearLayout root;

  @BindView(R.id.name)
  TextInputEditText name;

  @BindView(R.id.name_layout)
  TextInputLayout nameLayout;

  @BindView(R.id.progress_bar)
  ProgressBar progressView;

  private CaldavCalendar caldavCalendar;
  private CaldavAccount caldavAccount;

  @Override
  protected int getLayout() {
    return R.layout.activity_caldav_calendar_settings;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Intent intent = getIntent();
    caldavCalendar = intent.getParcelableExtra(EXTRA_CALDAV_CALENDAR);

    super.onCreate(savedInstanceState);

    if (caldavCalendar == null) {
      caldavAccount = intent.getParcelableExtra(EXTRA_CALDAV_ACCOUNT);
    } else {
      caldavAccount = caldavDao.getAccountByUuid(caldavCalendar.getAccount());
    }
    caldavAccount =
        caldavCalendar == null
            ? intent.getParcelableExtra(EXTRA_CALDAV_ACCOUNT)
            : caldavDao.getAccountByUuid(caldavCalendar.getAccount());

    if (savedInstanceState == null) {
      if (caldavCalendar != null) {
        name.setText(caldavCalendar.getName());
        selectedColor = caldavCalendar.getColor();
        selectedIcon = caldavCalendar.getIcon();
      }
    }

    if (caldavCalendar == null) {
      name.requestFocus();
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT);
    }

    updateTheme();
  }

  @Override
  protected boolean isNew() {
    return caldavCalendar == null;
  }

  @Override
  protected String getToolbarTitle() {
    return isNew() ? getString(R.string.new_list) : caldavCalendar.getName();
  }

  @OnTextChanged(R.id.name)
  void onNameChanged() {
    nameLayout.setError(null);
  }

  @Override
  protected void save() {
    if (requestInProgress()) {
      return;
    }

    String name = getNewName();

    if (isNullOrEmpty(name)) {
      nameLayout.setError(getString(R.string.name_cannot_be_empty));
      return;
    }

    if (caldavCalendar == null) {
      showProgressIndicator();
      createCalendar(caldavAccount, name, selectedColor);
    } else if (nameChanged() || colorChanged()) {
      showProgressIndicator();
      updateNameAndColor(caldavAccount, caldavCalendar, name, selectedColor);
    } else if (iconChanged()) {
      updateCalendar();
    } else {
      finish();
    }
  }

  protected abstract void createCalendar(CaldavAccount caldavAccount, String name, int color);

  protected abstract void updateNameAndColor(
      CaldavAccount account, CaldavCalendar calendar, String name, int color);

  protected abstract void deleteCalendar(
      CaldavAccount caldavAccount, CaldavCalendar caldavCalendar);

  private void showProgressIndicator() {
    progressView.setVisibility(View.VISIBLE);
  }

  private void hideProgressIndicator() {
    progressView.setVisibility(View.GONE);
  }

  private boolean requestInProgress() {
    return progressView.getVisibility() == View.VISIBLE;
  }

  protected void requestFailed(Throwable t) {
    hideProgressIndicator();

    if (t instanceof HttpException) {
      showSnackbar(t.getMessage());
    } else if (t instanceof DisplayableException) {
      showSnackbar(((DisplayableException) t).getResId());
    } else if (t instanceof ConnectException) {
      showSnackbar(R.string.network_error);
    } else {
      showSnackbar(R.string.error_adding_account, t.getMessage());
    }
  }

  private void showSnackbar(int resId, Object... formatArgs) {
    showSnackbar(getString(resId, formatArgs));
  }

  private void showSnackbar(String message) {
    Snackbar snackbar =
        Snackbar.make(root, message, 8000)
            .setTextColor(getColor(R.color.snackbar_text_color))
            .setActionTextColor(getColor(R.color.snackbar_action_color));
    snackbar
        .getView()
        .setBackgroundColor(getColor(R.color.snackbar_background));
    snackbar.show();
  }

  protected void createSuccessful(String url) {
    CaldavCalendar caldavCalendar = new CaldavCalendar();
    caldavCalendar.setUuid(UUIDHelper.newUUID());
    caldavCalendar.setAccount(caldavAccount.getUuid());
    caldavCalendar.setUrl(url);
    caldavCalendar.setName(getNewName());
    caldavCalendar.setColor(selectedColor);
    caldavCalendar.setIcon(selectedIcon);
    caldavDao.insert(caldavCalendar);
    setResult(
        RESULT_OK,
        new Intent().putExtra(MainActivity.OPEN_FILTER, new CaldavFilter(caldavCalendar)));
    finish();
  }

  protected void updateCalendar() {
    caldavCalendar.setName(getNewName());
    caldavCalendar.setColor(selectedColor);
    caldavCalendar.setIcon(selectedIcon);
    caldavDao.update(caldavCalendar);
    setResult(
        RESULT_OK,
        new Intent(TaskListFragment.ACTION_RELOAD)
            .putExtra(MainActivity.OPEN_FILTER, new CaldavFilter(caldavCalendar)));
    finish();
  }

  @Override
  protected boolean hasChanges() {
    return caldavCalendar == null
        ? !isNullOrEmpty(getNewName()) || selectedColor != 0 || selectedIcon != -1
        : nameChanged() || iconChanged() || colorChanged();
  }

  private boolean nameChanged() {
    return !caldavCalendar.getName().equals(getNewName());
  }

  private boolean colorChanged() {
    return selectedColor != caldavCalendar.getColor();
  }

  private boolean iconChanged() {
    return selectedIcon != caldavCalendar.getIcon();
  }

  private String getNewName() {
    return name.getText().toString().trim();
  }

  @Override
  public void finish() {
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(name.getWindowToken(), 0);
    super.finish();
  }

  @Override
  protected void discard() {
    if (!requestInProgress()) {
      super.discard();
    }
  }

  @Override
  protected void promptDelete() {
    if (!requestInProgress()) {
      super.promptDelete();
    }
  }

  @Override
  protected void delete() {
    showProgressIndicator();
    deleteCalendar(caldavAccount, caldavCalendar);
  }

  protected void onDeleted(boolean deleted) {
    if (deleted) {
      taskDeleter.delete(caldavCalendar);
      setResult(RESULT_OK, new Intent(TaskListFragment.ACTION_DELETED));
      finish();
    }
  }
}
