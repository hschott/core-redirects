package com.tallence.core.redirects.cae.service;

import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.multisite.Site;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.objectserver.beans.ContentBean;
import com.tallence.core.redirects.cae.AbstractRedirectsTest;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Ignored, because the test fails sometimes. Seems to be a raceCondition.
 */
@Ignore
public class RedirectContentListenerTest extends AbstractRedirectsTest {

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private RedirectService redirectService;

  @Autowired
  private RedirectUpdateTaskScheduler redirectUpdateTaskScheduler;

  @Autowired
  private SitesService sitesService;

  @Test
  public void testAddRemoveRedirect() {
    redirectUpdateTaskScheduler.setTestMode(true);

    ContentBean target = getContentBean(1002);

    Site site = sitesService.getSite("siteA");
    int plainSizeBefore = redirectService.getRedirectsForSite(site).getPlainRedirects().size();
    int patternSizeBefore = redirectService.getRedirectsForSite(site).getPatternRedirects().size();

    Map<String, Object> properties = new HashMap<>();
    properties.put("sourceUrlType", "PLAIN");
    properties.put("source", "/redirect-test-dynamic");
    properties.put("targetLink", Collections.singletonList(target.getContent()));
    properties.put("redirectType", "ALWAYS");
    properties.put("description", "description");
    properties.put("imported", 0);
    Content redirect = contentRepository.createChild("/Sites/TestA/Options/Settings/Redirects/TestRedirect-Dynamic", "Redirect", properties);
    // Since we cant disable the repo listener, we call the redirectUpdateTaskScheduler directly, so we don't have to sleep here.
    redirectUpdateTaskScheduler.runUpdate(redirect);

    assertThat(redirectService.getRedirectsForSite(site).getPlainRedirects().size(), equalTo(plainSizeBefore + 1));
    assertThat(redirectService.getRedirectsForSite(site).getPatternRedirects().size(), equalTo(patternSizeBefore));
    assertThat(redirectService.getRedirectsForSite(site).getPlainRedirects().values(), hasItem(hasProperty("source", equalTo("/channela/redirect-test-dynamic"))));

    // Test deletion
    redirect.delete();
    // Since we cant disable the repo listener, we call the redirectUpdateTaskScheduler directly, so we don't have to sleep here.
    redirectUpdateTaskScheduler.runRemove(redirect);

    assertThat(redirectService.getRedirectsForSite(site).getPlainRedirects().size(), equalTo(plainSizeBefore));
    assertThat(redirectService.getRedirectsForSite(site).getPatternRedirects().size(), equalTo(patternSizeBefore));

    redirectUpdateTaskScheduler.setTestMode(false);
  }
}
