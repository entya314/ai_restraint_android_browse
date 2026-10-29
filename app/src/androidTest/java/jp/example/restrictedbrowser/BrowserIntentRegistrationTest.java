package jp.example.restrictedbrowser;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.List;

/** Verifies Android's real intent resolver; requires a device, not counted as host tests. */
@RunWith(AndroidJUnit4.class)
public class BrowserIntentRegistrationTest {
    @Test public void browserSelectorResolvesToMainActivity() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER).setPackage(context.getPackageName());
        List<ResolveInfo> matches=context.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY);
        assertEquals(1,matches.size());
        assertEquals(MainActivity.class.getName(),matches.get(0).activityInfo.name);
    }
    @Test public void arbitraryHttpsLinksResolveToBrowser() { assertBrowserHandler("https://external.example.net/arbitrary/path?q=1"); }
    @Test public void httpLinksResolveBeforeAppRejectsCleartext() { assertBrowserHandler("http://external.example.net/arbitrary/path"); }
    private void assertBrowserHandler(String url) {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.getPackageName());
        List<ResolveInfo> matches=context.getPackageManager().queryIntentActivities(intent,
            PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER);
        assertEquals(1,matches.size());ResolveInfo match=matches.get(0);
        assertEquals(MainActivity.class.getName(),match.activityInfo.name);
        assertNotNull(match.filter);
        assertEquals("Must not restrict browser registration to selected hosts",0,match.filter.countDataAuthorities());
        assertEquals(0,match.filter.countDataPaths());
        assertTrue(match.filter.hasDataScheme("http"));
        assertTrue(match.filter.hasDataScheme("https"));
        assertTrue(match.activityInfo.exported);
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK,match.activityInfo.launchMode);
    }
    @Test public void fileUrlsHaveNoBrowsableHandler() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("file:///sdcard/test.html")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.getPackageName());
        assertTrue(context.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY).isEmpty());
    }
}
