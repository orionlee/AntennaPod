package de.danoeh.antennapod;

import android.app.Application;
import android.os.Build;
import android.os.StrictMode;

import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.joanzapata.iconify.fonts.MaterialModule;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapod.core.feed.EventDistributor;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.spa.SPAUtil;

/** Main application class. */
public class PodcastApp extends Application {

    // make sure that ClientConfigurator executes its static code
    static {
        try {
            Class.forName("de.danoeh.antennapod.config.ClientConfigurator");
        } catch (Exception e) {
            throw new RuntimeException("ClientConfigurator not found");
        }
    }

	private static PodcastApp singleton;

	public static PodcastApp getInstance() {
		return singleton;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		Thread.setDefaultUncaughtExceptionHandler(new CrashReportWriter());

		if(BuildConfig.DEBUG) {
			StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder()
				.detectLeakedSqlLiteObjects()
				.penaltyLog()
				.penaltyDropBox();
			builder.detectActivityLeaks();
			builder.detectLeakedClosableObjects();
			if(Build.VERSION.SDK_INT >= 16) {
				builder.detectLeakedRegistrationObjects();
			}
			StrictMode.setVmPolicy(builder.build());
		}

		singleton = this;

		ClientConfig.initialize(this);

		// PROTOTYPE ONLY: feed with high priority
		Feed.priorityProvider = (feed) -> {
			final List<String> highPriTitles = Arrays.asList(
					"The Record",
					"Up First",
					"The Daily",
					"Science Friday",
					"CBC News: The World This Weekend"
			);
			if (highPriTitles.contains(feed.getTitle())) {
				return Feed.PRIORITY_HIGH;
			} else {
				return Feed.PRIORITY_NORMAL;
			}
		};

		EventDistributor.getInstance();
		Iconify.with(new FontAwesomeModule());
		Iconify.with(new MaterialModule());

        SPAUtil.sendSPAppsQueryFeedsIntent(this);
    }

}
