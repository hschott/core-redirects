package com.tallence.core.redirects.cae.filter;


import com.tallence.core.redirects.cae.model.Redirect;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Internal wrapper class to prevent to response from being committed too early.
 *
 * This wrapper stores all written data in internal variables and prevents them
 * from being written on the wire, so that the final response code can be checked
 * (in this case for a 404) and rewritten.
 * Otherwise, changing the response code would not be possible anymore, once
 * control is returned to the redirect filter.
 */
class RedirectHttpServletResponseWrapper extends HttpServletResponseWrapper {
  private int tempStatus = 200;
  private String tempMsg;
  private String tempLocation;
  private boolean isError;
  private boolean isRedirect;

  // Reference to the redirect which this wrapper is used for
  private final Redirect redirect;

  private CharArrayWriter writer = new CharArrayWriter();

  public RedirectHttpServletResponseWrapper(HttpServletResponse response, Redirect redirect) {
    super(response);
    this.redirect = redirect;
  }

  public Redirect getRedirect() {
    return redirect;
  }

  @Override
  public void setStatus(int sc) {
    tempStatus = sc;
  }

  @Override
  public int getStatus() {
    return tempStatus;
  }

  @Override
  public void sendError(int sc, String msg) {
    tempStatus = sc;
    tempMsg = msg;
    isError = true;
  }

  @Override
  public void sendError(int sc) {
    tempStatus = sc;
    isError = true;
  }

  @Override
  public void sendRedirect(String location) {
    tempLocation = location;
    isRedirect = true;
  }

  @Override
  public PrintWriter getWriter() {
    return new PrintWriter(writer);
  }

  public String toString() {
    return writer.toString();
  }

  /**
   * Writes the stored request on the wire (by writing the data on the super instance,
   * which writes it to the original request to be sent).
   */
  public void writeOnSuper() throws IOException {
    if (isError) {
      if (tempMsg != null) {
        super.sendError(tempStatus, tempMsg);
      } else {
        super.sendError(tempStatus);
      }
    } else if (isRedirect) {
      super.sendRedirect(tempLocation);
    } else {
      super.setStatus(tempStatus);
      String result = writer.toString();
      super.setContentLength(result.length());
      super.getWriter().write(result);
    }
  }


}