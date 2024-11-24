package com.demo;

import com.demo.geetest.GeetestRequest;
import com.demo.geetest.GeetestLib;
import com.demo.geetest.GeetestV3;
import com.google.gson.JsonObject;
import okhttp3.Request;

import java.util.Map;

public final class V3ClickDemo {
  public static void main(String[] args) {
    // v3图标点选
    var icon = newCaptcha("https://account.geetest.com/api/captchademo?captcha_type=click:icon-l1-zh");
    var iconData = GeetestRequest.of(icon.getKey(), icon.getValue());
    var iconClick = new GeetestV3(iconData);
    System.out.println(iconClick.crack());

    // v3文字点选
    var word = newCaptcha("https://www.geetest.com/demo/gt/register-click-official?t=" + System.currentTimeMillis());
    var wordData = GeetestRequest.of(word.getKey(), word.getValue());
    var wordClick = new GeetestV3(wordData);
    System.out.println(wordClick.crack());

    // v3语序点选
    var phrase = newCaptcha("https://account.geetest.com/api/captchademo?captcha_type=click:phrase-l1-zh");
    var phraseData = GeetestRequest.of(phrase.getKey(), phrase.getValue());
    var phraseClick = new GeetestV3(phraseData);
    System.out.println(phraseClick.crack());

    // okhttp线程池不会立马退出
    System.exit(0);
  }

  @SuppressWarnings("DataFlowIssue")
  public static Map.Entry<String, String> newCaptcha(String url) {
    Request request = new Request.Builder().url(url).build();
    JsonObject res = GeetestLib.execute(request, GeetestLib.okhttp);
    return Map.entry(
      res.has("gt") ? res.get("gt").getAsString() : res.get("data").getAsJsonObject().get("gt").getAsString(),
      res.has("challenge") ? res.get("challenge").getAsString() : res.get("data").getAsJsonObject().get("challenge").getAsString()
    );
  }
}