package com.demo;

import com.demo.geetest.GeetestRequest;
import com.demo.geetest.GeetestLib;
import com.demo.geetest.GeetestV3;
import com.google.gson.JsonObject;
import okhttp3.Request;

import java.util.Map;

public final class V3SlideDemo {
  public static void main(String[] args) {
    // v3滑动验证码
    var slide = newCaptcha("https://www.geetest.com/demo/gt/register-test?t=" + System.currentTimeMillis());
    var iconData = GeetestRequest.of(slide.getKey(), slide.getValue());
    var s = new GeetestV3(iconData);
    System.out.println(s.crack());

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
