package com.demo.geetest;

import java.util.ArrayList;
import java.util.List;

/** 极验验证结果，challenge可能会改变，以此类中的challenge为准 */
public final class GeetestResult {
  public String gt;
  public String challenge;
  /** 此处不为null则代表验证码已验证成功 */
  public String validate;

  // 额外信息
  public CaptchaType captchaType;
  public List<String> captchaImages = new ArrayList<>();

  public long totalMillis;
  public long inferenceMillis;
  public long wGenerationMillis;

  @Override
  public String toString() {
  return "GeetestResult(" +
    "gt=" + gt +
    ", challenge=" + challenge +
    ", validate=" + validate +
    ", captchaType=" + captchaType +
    ", captchaImages=" + captchaImages +
    ", totalMillis=" + totalMillis +
    ", inferenceMillis=" + inferenceMillis +
    ", wGenerationMillis=" + wGenerationMillis +
    ')';
  }
}
