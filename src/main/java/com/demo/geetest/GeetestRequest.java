package com.demo.geetest;

public final class GeetestRequest {
  /** 极验验证gt固定id */
  /*package*/String gt;
  /** 极验验证流水单号 */
  /*package*/String challenge;
  /** 与极验服务器交互使用的ua, 可为空 */
  /*package*/String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";
  /** referer请求头, 可为空 */
  /*package*/String referer;
  /** 使用的代理url，可为空, 极验对单IP下的验证会有限制，需注意 */
  /*package*/String proxyUrl;
  /** 极验返回失败时重试的次数 */
  /*package*/int retryCount = 3;

  public GeetestRequest gt(String gt) {
    this.gt = gt;
    return this;
  }

  public GeetestRequest challenge(String challenge) {
    this.challenge = challenge;
    return this;
  }

  public GeetestRequest userAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }
  public GeetestRequest referer(String referer) {
    this.referer = referer;
    return this;
  }
  public GeetestRequest proxyUrl(String proxyUrl) {
    this.proxyUrl = proxyUrl;
    return this;
  }
  public GeetestRequest retryCount(int retryCount) {
    this.retryCount = retryCount;
    return this;
  }

  @Override
  public String toString() {
    return "GeetestData(" + "gt='" + gt + '\'' +
      ", challenge='" + challenge + '\'' +
      ", userAgent='" + userAgent + '\'' +
      ", referer='" + referer + '\'' +
      ", proxyUrl='" + proxyUrl + '\'' +
      ", retryCount=" + retryCount +
      ')';
  }

  public static GeetestRequest of(String gt, String challenge) {
    GeetestRequest result = new GeetestRequest();
    result.gt = gt;
    result.challenge = challenge;
    return result;
  }
}