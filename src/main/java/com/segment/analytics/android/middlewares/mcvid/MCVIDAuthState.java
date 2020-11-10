package com.segment.analytics.android.middlewares.mcvid;

/**
 * Visitor Authentication States in Audience Manager.
 *
 * @see "https://docs.adobe.com/content/help/en/id-service/using/reference/authenticated-state.html"
 */
public enum MCVIDAuthState {
  // Unknown or never authenticated.
  MCVIDAuthStateUnknown(0),

  // Authenticated for a particular instance, page, or app.
  MCVIDAuthStateAuthenticated(1),

  // Logged out.
  MCVIDAuthStateLoggedOut(2);

  private final int state;

  MCVIDAuthState(int state) {
    this.state = state;
  }

  public int getState() {
    return state;
  }

  @Override
  public String toString() {
    return "MCVIDAuthState{" +
        "state=" + state +
        '}';
  }
}
