package com.demo.geetest;

/** 验证码类型 */
public enum CaptchaType {
  V3Slide,
  V3ClickIcon,
  V3ClickWord,
  V3ClickPhrase;

  public static CaptchaType forClick(String type) {
    switch (type) {
      case "word": return V3ClickWord;
      case "icon": return V3ClickIcon;
      case "phrase": return V3ClickPhrase;
      default: return null;
    }
  }
}