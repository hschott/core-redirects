package com.tallence.core.redirects.cae.service;

import com.tallence.core.redirects.cae.model.Redirect;
import com.tallence.core.redirects.model.SourceUrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Holder class for the redirects of a specific site.
 * Keeps maps of the paths or patterns to their redirects.
 */
public class SiteRedirects {

  private static final Logger LOG = LoggerFactory.getLogger(SiteRedirects.class);

  private String siteId;
  private Map<String, Redirect> staticRedirects = new LinkedHashMap<>();
  private Map<Pattern, Redirect> patternRedirects = new LinkedHashMap<>();

  public SiteRedirects() {
  }

  public SiteRedirects(String siteId) {
    this.siteId = siteId;
  }

  /**
   * Returns the list of static redirects.
   */
  public Map<String, Redirect> getStaticRedirects() {
    return staticRedirects;
  }

  /**
   * Add a simple (static)
   */
  public void addStaticRedirect(Redirect redirect) {
    if (redirect.getSourceUrlType() != SourceUrlType.ABSOLUTE) {
      LOG.error("Illegal source type on rule {}, ignoring redirect", redirect);
    }
    staticRedirects.put(redirect.getSource(), redirect);
  }

  /**
   * Returns the list of redirects with pattern source urls.
   */
  public Map<Pattern, Redirect> getPatternRedirects() {
    return patternRedirects;
  }

  /**
   * Adds pattern redirect to map, if it contains a valid pattern.
   */
  public void addPatternRedirect(Redirect redirect) {
    if ((redirect.getSourceUrlType() != SourceUrlType.REGEX)) {
      LOG.error("Illegal source type on rule {}, ignoring redirect", redirect);
    }

    try {
      patternRedirects.put(Pattern.compile(redirect.getSource()), redirect);
    } catch (PatternSyntaxException e) {
      LOG.error("Unable to compile pattern on redirect {}, ignoring redirect", redirect);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SiteRedirects that = (SiteRedirects) o;
    return Objects.equals(staticRedirects, that.staticRedirects) &&
            Objects.equals(patternRedirects, that.patternRedirects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(staticRedirects, patternRedirects);
  }

  @Override
  public String toString() {
    return "SiteRedirects{" +
            "siteId='" + siteId + '\'' +
            ", staticRedirects.size=" + staticRedirects.size() +
            ", patternRedirects.size=" + patternRedirects.size() +
            '}';
  }
}