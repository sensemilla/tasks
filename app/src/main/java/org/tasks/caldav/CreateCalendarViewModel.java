package org.tasks.caldav;

import org.tasks.data.CaldavAccount;
import org.tasks.ui.CompletableViewModel;

@SuppressWarnings("WeakerAccess")
public class CreateCalendarViewModel extends CompletableViewModel<String> {
  void createCalendar(CaldavClient client, CaldavAccount account, String name, int color) {
    run(() -> client.forAccount(account).makeCollection(name, color));
  }
}
