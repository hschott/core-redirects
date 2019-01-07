package com.tallence.core.redirects.cae.filter;

import com.coremedia.blueprint.base.multisite.SiteHelper;
import com.coremedia.blueprint.base.multisite.SiteResolver;
import com.coremedia.cap.multisite.Site;
import com.coremedia.objectserver.beans.ContentBean;
import com.coremedia.objectserver.beans.ContentBeanFactory;
import com.coremedia.objectserver.web.links.LinkFormatter;
import com.tallence.core.redirects.cae.model.Redirect;
import com.tallence.core.redirects.cae.service.RedirectService;
import com.tallence.core.redirects.cae.service.SiteRedirects;
import com.tallence.core.redirects.model.RedirectType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Filter for the handling of redirects.
 */
@Service
public class RedirectFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(RedirectFilter.class);

  private final ContentBeanFactory contentBeanFactory;
  private final SiteResolver siteResolver;
  private final RedirectService redirectService;
  private final LinkFormatter linkFormatter;

  @Autowired
  public RedirectFilter(ContentBeanFactory contentBeanFactory,
                        SiteResolver siteResolver,
                        RedirectService redirectService,
                        LinkFormatter linkFormatter) {
    this.contentBeanFactory = contentBeanFactory;
    this.siteResolver = siteResolver;
    this.redirectService = redirectService;
    this.linkFormatter = linkFormatter;
  }


  /**
   * Match the request for possible redirects and handle accordingly.
   */
  @Override
  public void doFilter(ServletRequest srequest, ServletResponse sresponse, FilterChain chain) throws IOException, ServletException {
    // Cast
    HttpServletRequest request = (HttpServletRequest) srequest;
    HttpServletResponse response = (HttpServletResponse) sresponse;
    RedirectHttpServletResponseWrapper wrapper = null;

    // Fetch redirects
    SiteRedirects redirects = getSiteRedirects(request);

    // Pre-handle
    Result result = determinePreAction(redirects, request.getPathInfo());
    if (result.action == Result.Action.SEND) {
      sendPermanentRedirect(request, response, result.redirect);
    } else if (result.action == Result.Action.WRAP) {
      // Because we might have to modify the response, we need to wrap it in order to prevent tomcat from starting
      // to write to the wire before we have inspected it.
      wrapper = new RedirectHttpServletResponseWrapper(response, result.redirect);
    }

    // Let the actual controller do its thing (with the wrapper, if one is set)
    chain.doFilter(request, wrapper == null ? response : wrapper);

    // Posthandle only on 404
    if (wrapper != null) {
      if (wrapper.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
        // Ignore response and send redirect
        Redirect redirect = wrapper.getRedirect();
        sendPermanentRedirect(request, response, redirect);
      } else {
        // Write cached status code to response
        wrapper.writeOnSuper();
      }
    }
  }


  // FILTER DEFAULT METHODS

  @Override
  public void init(FilterConfig filterConfig) {
    // Nothing to do here
  }

  @Override
  public void destroy() {
    // Nothing to do here
  }


  // HELPER METHODS

  /**
   * Fetch redirects for the given site.
   *
   * @param request current request
   * @return a result holder
   */
  @NonNull
  private SiteRedirects getSiteRedirects(HttpServletRequest request) {
    // Determine site (in order to fetch the redirects for it)
    Site site = getSiteFromRequest(request);

    // Fetch redirect holder for site
    return redirectService.getRedirectsForSite(site);
  }

  /**
   * Because the {@code SiteFilter} might have run or not (depending on the setup), we can either simply take
   * the current site from the request or we have to parse it outselves.
   */
  @Nullable
  private Site getSiteFromRequest(HttpServletRequest request) {
    // Check first, if someone has already made the lookup
    Site site = SiteHelper.getSiteFromRequest(request);

    if (site == null) {
      // If site is not in request, it means that the SiteFilter has not run yet. So we fetch the site ourselves.
      // This code is shamelessly copied from SiteFilter.java in cae-base-lib
      String pathInfo = request.getPathInfo();
      try {
        if (!StringUtils.hasLength(pathInfo)) {
          LOG.info("Could not determine a site without a path info in request {}", request);
        } else {
          site = siteResolver.findSiteByPath(pathInfo);
        }
      } catch (Exception e) {
        LOG.warn("Could not determine the site for the request", e);
      }

    }
    return site;
  }

  /**
   * Determines, if a redirect should be executed now, after handling or never.
   */
  private Result determinePreAction(SiteRedirects redirects, String pathInfo) {
    if (redirects.getStaticRedirects().containsKey(pathInfo)) {
      Redirect redirect = redirects.getStaticRedirects().get(pathInfo);
      return redirect.getRedirectType() == RedirectType.ALWAYS ? Result.send(redirect) : Result.wrap(redirect);
    } else {
      for (Map.Entry<Pattern, Redirect> patternRedirect : redirects.getPatternRedirects().entrySet()) {
        if (patternRedirect.getKey().matcher(pathInfo).matches()) {
          Redirect redirect = patternRedirect.getValue();
          return redirect.getRedirectType() == RedirectType.ALWAYS ? Result.send(redirect) : Result.wrap(redirect);
        }
      }
    }
    return Result.none();
  }

  /**
   * Executes the actual redirect.
   * TODO Currently, this code always does a 301 with instant expiry. This should be made configurable.
   */
  private void sendPermanentRedirect(HttpServletRequest request, HttpServletResponse response, Redirect target) {
    if (target.getTarget() == null) {
      LOG.error("Unable to redirect to empty string for redirect {}", target);
      throw new RedirectConfigurationException("Unable to redirect to empty string");
    }

    LOG.debug("Redirecting to {}", target);

    // Reset content and headers
    response.reset();

    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
    response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    response.setDateHeader(HttpHeaders.EXPIRES, 0);
    ContentBean targetBean = contentBeanFactory.createBeanFor(target.getTarget());
    response.setHeader(HttpHeaders.LOCATION, linkFormatter.formatLink(targetBean, null, request, response, true));
  }

  /**
   * Simple wrapper class for the pre-handle redirect result.
   */
  private static class Result {
    private enum Action {
      NONE, SEND, WRAP
    }

    private Redirect redirect;
    private Action action;

    private Result(Redirect redirect, Action action) {
      this.redirect = redirect;
      this.action = action;
    }

    static Result send(Redirect redirect) {
      return new Result(redirect, Action.SEND);
    }

    static Result wrap(Redirect redirect) {
      return new Result(redirect, Action.WRAP);
    }

    static Result none() {
      return new Result(null, Action.NONE);
    }
  }
}