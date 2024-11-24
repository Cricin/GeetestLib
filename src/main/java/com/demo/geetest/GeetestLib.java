package com.demo.geetest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * 通用资源和工具代码
 */
public final class GeetestLib {
  private static final Logger logger = LoggerFactory.getLogger(GeetestLib.class);
  public static final Gson gson = new Gson();
  public static final OkHttpClient okhttp = initializeOkhttp();

  private static OkHttpClient initializeOkhttp() {
    Dispatcher dispatcher = new Dispatcher();
    // 设置okhttp没有并发请求上限
    dispatcher.setMaxRequests(Integer.MAX_VALUE);
    dispatcher.setMaxRequestsPerHost(Integer.MAX_VALUE);
    Interceptor ua = chain -> {
      if (chain.request().header("User-Agent") == null) {
        return chain.proceed(chain.request().newBuilder()
          // 添加默认UA, Edge浏览器131版本
          .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
          .build());
      } else {
        return chain.proceed(chain.request());
      }
    };
    return new OkHttpClient.Builder().dispatcher(dispatcher).addInterceptor(ua).build();
  }

  public static JsonObject execute(Request request, OkHttpClient okhttp) {
    String jsonP = request.url().queryParameter("callback");
    // 处理jsonPadding

    try (Response res = okhttp.newCall(request).execute()) {
      if (!res.isSuccessful()) {
        logger.error("请求失败, code={}, message={}, url={}", res.code(), res.message(), request.url());
        return null;
      }
      if (res.body() == null) {
        logger.error("请求失败，response body == null");
        return null;
      }
      String json = res.body().string();


      if (jsonP != null) {
        json = json.substring(jsonP.length() + 1, json.length() - 1);
      }
      return gson.fromJson(json, JsonObject.class);
    } catch (IOException e) {
      logger.warn("请求发生IO异常", e);
      return null;
    }
  }

  public static byte[] executeToByteArray(Request request, OkHttpClient okhttp) {
    try (Response res = okhttp.newCall(request).execute()) {
      if (!res.isSuccessful()) {
        logger.error("获取验证码图片失败, code={}, message={}", res.code(), res.message());
        return null;
      }
      if (res.body() == null) {
        logger.error("获取验证码图片失败，response body == null");
        return null;
      }
      return res.body().bytes();
    } catch (IOException e) {
      logger.warn("请求发生IO异常", e);
      return null;
    }
  }

}