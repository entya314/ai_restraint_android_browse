package jp.example.restrictedbrowser;

import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

/** Keep editing actions while excluding share, web search and external PROCESS_TEXT activities. */
public final class RestrictedWebView extends WebView {
    public RestrictedWebView(Context context) { super(context); }
    private ActionMode.Callback filter(ActionMode.Callback original) {
        return new ActionMode.Callback() {
            private void trim(Menu menu) {
                for (int i = menu.size() - 1; i >= 0; i--) {
                    int id = menu.getItem(i).getItemId();
                    if (id != android.R.id.copy && id != android.R.id.cut &&
                            id != android.R.id.paste && id != android.R.id.selectAll &&
                            id != android.R.id.pasteAsPlainText) menu.removeItem(id);
                }
            }
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                boolean result = original.onCreateActionMode(mode, menu); trim(menu); return result;
            }
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                boolean result = original.onPrepareActionMode(mode, menu); trim(menu); return result;
            }
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id != android.R.id.copy && id != android.R.id.cut && id != android.R.id.paste &&
                        id != android.R.id.selectAll && id != android.R.id.pasteAsPlainText) return true;
                return original.onActionItemClicked(mode, item);
            }
            public void onDestroyActionMode(ActionMode mode) { original.onDestroyActionMode(mode); }
        };
    }
    @Override public ActionMode startActionMode(ActionMode.Callback cb) { return super.startActionMode(filter(cb)); }
    @Override public ActionMode startActionMode(ActionMode.Callback cb, int type) { return super.startActionMode(filter(cb), type); }
}
