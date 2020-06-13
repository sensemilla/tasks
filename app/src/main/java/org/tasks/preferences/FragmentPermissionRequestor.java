package org.tasks.preferences;

import androidx.fragment.app.Fragment;
import javax.inject.Inject;

public class FragmentPermissionRequestor extends PermissionRequestor {

  private final Fragment fragment;

  @Inject
  public FragmentPermissionRequestor(Fragment fragment, PermissionChecker permissionChecker) {
    super(permissionChecker);

    this.fragment = fragment;
  }

  @Override
  protected void requestPermissions(int rc, String... permissions) {
    fragment.requestPermissions(permissions, rc);
  }
}
