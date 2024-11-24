package com.demo.geetest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 支持3代滑动和空间推理以外的点选
 */
public final class GeetestV3 {
  private static final Logger logger = LoggerFactory.getLogger(GeetestV3.class);

  static final class ImagePicAndData {
    String pic;
    byte[] data;
  }

  private final GeetestRequest request;
  private final GeetestResult result = new GeetestResult();
  private final OkHttpClient okhttp;
  private final AtomicBoolean executed = new AtomicBoolean(false);

  public GeetestV3(GeetestRequest request) {
    this.request = request;
    if (request.proxyUrl != null) {
      okhttp = ProxyUrl.parse(request.proxyUrl).apply(GeetestLib.okhttp);
    } else {
      okhttp = GeetestLib.okhttp;
    }
  }

  public GeetestResult crack() {
    if (!executed.compareAndSet(false, true)) {
      throw new IllegalStateException("already executed!");
    }

    long startTime = System.currentTimeMillis();
    try {
      result.gt = request.gt;
      result.challenge = request.challenge;

      logger.debug("Step1: 获取验证类型");
      var getTypeRequest = newRequestBuilder(
        "https://apiv6.geetest.com/gettype.php?gt=%s&callback=%s",
        request.gt,
        newGeetestCallback()
      ).build();
      JsonObject getTypeRes = GeetestLib.execute(getTypeRequest, okhttp);
      // logger.debug("验证类型Res = {}", getTypeRes);
      if (getTypeRes == null || !"success".equals(getTypeRes.get("status").getAsString())) {
        logger.warn("无法获取验证类型, res={}", getTypeRes);
        return null;
      }
      String type = getTypeRes.get("data").getAsJsonObject().get("type").getAsString();
      if ("fullpage".equals(type)) {
        return runFullPage();
      } else if ("click".equals(type)) {
        return runClick(false);
      } else if ("slide".equals(type)) {
        return runSlide(false);
      } else {
        logger.error("不支持的验证类型: {}", type);
        return null;
      }
    } finally {
      result.totalMillis = System.currentTimeMillis() - startTime;
    }
  }

  private GeetestResult runFullPage() {
    // 先请求一次get接口
    logger.debug("Step2: 获取无感验证参数");
    var getRequest = newRequestBuilder(
      "https://apiv6.geetest.com/get.php?gt=%s&challenge=%s&lang=zh-cn&pt=0&client_type=web&w=&callback=%s",
      request.gt,
      request.challenge,
      newGeetestCallback()
    ).build();
    GeetestLib.execute(getRequest, okhttp);
    //logger.debug("无感验证参数Res={}", getRes);
    logger.debug("Step3: 无感验证提交");
    // 在调用ajax接口
    var ajaxRequest = newRequestBuilder(
      "https://api.geevisit.com/ajax.php?gt=%s&challenge=%s&lang=zh-cn&pt=0&client_type=web&w=&callback=%s",
      request.gt,
      request.challenge,
      newGeetestCallback()
    ).build();

    JsonObject ajaxRes = GeetestLib.execute(ajaxRequest, okhttp);
    if (ajaxRes == null || !"success".equals(ajaxRes.get("status").getAsString())) {
      logger.warn("无感验证失败! res={}", ajaxRes);
      return null;
    }

    String nextType = ajaxRes.get("data").getAsJsonObject().get("result").getAsString();
    if ("click".equals(nextType)) {
      logger.info("后续验证类型是点选");
      return runClick(true);
    } else if ("slide".equals(nextType)) {
      return runSlide(true);
    } else {
      logger.error("不支持的后续验证类型: {}", nextType);
      return null;
    }
  }

  private GeetestResult runClick(boolean isNext) {
    // 先请求一次get接口
    logger.debug("Step4: 获取点选验证参数");

    var getRequest = newRequestBuilder(
      "https://api.geevisit.com/get.php?is_next=%s&type=%s&gt=%s&challenge=%s&lang=zh-cn&https=true" +
        "&protocol=%s&offline=false&product=float&api_server=%s&isPC=true&autoReset=true&width=%s&w=&callback=%s",
      String.valueOf(isNext),
      "click",
      request.gt,
      request.challenge,
      URLEncoder.encode("https://", StandardCharsets.UTF_8),
      URLEncoder.encode("api.geevisit.com", StandardCharsets.UTF_8),
      URLEncoder.encode("100%", StandardCharsets.UTF_8),
      newGeetestCallback()
    ).build();

    JsonObject getRes = GeetestLib.execute(getRequest, okhttp);
    // logger.debug("点选验证参数为: {}", getRes);
    if (getRes == null || !"success".equals(getRes.get("status").getAsString())) {
      logger.warn("无法获取点选参数, res={}", getRes);
      return null;
    }
    CaptchaType type = CaptchaType.forClick(getRes.get("data").getAsJsonObject().get("pic_type").getAsString());
    result.captchaType = type;

    logger.debug("极验点选子类型是: {}", type);

    if (type == null) {
      logger.error("不支持的点选类型: {}", getRes.get("data").getAsJsonObject().get("pic_type").getAsString());
      return null;
    }

    ImagePicAndData ipad = new ImagePicAndData();
    ipad.pic = getRes.get("data").getAsJsonObject().get("pic").getAsString();

    // 加载验证图片
    logger.debug("Step5: 加载图片");

    JsonArray servers = getRes.get("data").getAsJsonObject().get("static_servers").getAsJsonArray();
    for (JsonElement server : servers) {
      String fullPicUrl = "http://" + server.getAsString() + ipad.pic.substring(1);
      logger.info("加载验证图片, 地址为: {}", fullPicUrl);
      ipad.data = GeetestLib.executeToByteArray(newRequestBuilder(fullPicUrl).build(), okhttp);
      if (ipad.data != null) {
        result.captchaImages.add(fullPicUrl);
        break;
      }
      logger.warn("加载验证图片失败...");
    }

    if (ipad.data == null) {
      logger.error("无法加载验证图片");
      return null;
    }

    // 识别验证码
    logger.debug("Step6: 识别验证码");
    long startTime = System.currentTimeMillis();

    if (ClickInferenceService.isAvailable()) {
      result.validate = runLocalGeetest(ipad, type, startTime);
    } else {
      logger.info("没有打码服务可用...");
    }
    return result;
  }

  private String runLocalGeetest(ImagePicAndData ipad, CaptchaType type, long startTime) {
    for (int i = 0; i < request.retryCount; i++) {
      if (i != 0) {
        logger.debug("正在刷新验证码并重试...");
        ipad = refreshImage();
        if (ipad == null) {
          logger.error("无法刷新验证码, 取消重试...");
          break;
        }
        startTime = System.currentTimeMillis();
      }
      String points = ClickInferenceService.inference(logger, ipad.data, type);
      result.inferenceMillis = System.currentTimeMillis() - startTime;

      if (points == null) {
        logger.warn("本地打码识别失败!");
        continue;
      }

      long timeCost = randomDelay(startTime);
      logger.info("正在生成w值...");

      long s = System.currentTimeMillis();
      String w = JsEngine.V3Click.w(request.gt, request.challenge, ipad.pic, timeCost, points);
      result.wGenerationMillis = System.currentTimeMillis() - s;

      if (w == null) {
        logger.error("无法生成w参数");
        return null;
      }

      String validate = validate(request.challenge, w);
      if (validate != null) return validate;
    }
    return null;
  }

  /**
   * 刷新验证图片, 返回所有图片地址
   */
  private ImagePicAndData refreshImage() {
    var refresh = newRequestBuilder(
      "https://api.geevisit.com/refresh.php?gt=%s&challenge=%s&lang=zh-cn&type=click&callback=%s",
      request.gt,
      request.challenge,
      newGeetestCallback()
    ).build();

    JsonObject res = GeetestLib.execute(refresh, okhttp);
    if (res == null || !"success".equals(res.get("status").getAsString())) {
      logger.warn("刷新验证码失败!");
      return null;
    }
    JsonArray servers = res.get("data").getAsJsonObject().get("image_servers").getAsJsonArray();

    ImagePicAndData ipad = new ImagePicAndData();
    ipad.pic = res.get("data").getAsJsonObject().get("pic").getAsString();
    String fullPicUrl = "http://" + servers.get(0).getAsString() + ipad.pic;
    ipad.data = GeetestLib.executeToByteArray(newRequestBuilder(fullPicUrl).build(), okhttp);
    result.captchaImages.add(fullPicUrl);
    return ipad;
  }

  /**
   * 加2.5到3.5秒的随机延迟
   */
  private long randomDelay(long startTime) {
    long timeCost = System.currentTimeMillis() - startTime;
    if (timeCost < 2000) {
      long randTime = 3000 - ThreadLocalRandom.current().nextInt(1000);
      long sleepTime = randTime - timeCost;
      timeCost = randTime;
      if (sleepTime > 0) {
        logger.debug("由于极验限制，识别完成后随机2000-3000ms提交验证. sleepTime={}ms", sleepTime);
        try {
          Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {
        }
      }
    }
    return timeCost;
  }

  private GeetestResult runSlide(boolean isNext) {
    // 先请求一次get接口
    logger.debug("Step4: 获取滑动验证参数");

    var getRequest = newRequestBuilder(
      "https://api.geevisit.com/get.php?is_next=%s&type=%s&gt=%s&challenge=%s&lang=zh-cn&https=true" +
        "&protocol=%s&offline=false&product=float&api_server=%s&isPC=true&autoReset=true&width=%s&w=&callback=%s",
      String.valueOf(isNext),
      "slide3",
      request.gt,
      request.challenge,
      URLEncoder.encode("https://", StandardCharsets.UTF_8),
      URLEncoder.encode("api.geevisit.com", StandardCharsets.UTF_8),
      URLEncoder.encode("100%", StandardCharsets.UTF_8),
      newGeetestCallback()
    ).build();

    JsonObject getRes = GeetestLib.execute(getRequest, okhttp);
    // logger.debug("点选验证参数为: {}", getRes);
    if (getRes == null) {
      logger.warn("无法获取滑动验证参数, res == null");
      return null;
    }

    // 提交验证时需要用到的参数
    String challenge = result.challenge = getRes.get("challenge").getAsString();
    String s = getRes.get("s").getAsString();
    JsonArray a = getRes.get("c").getAsJsonArray();
    int[] c = new int[a.size()];
    int i = 0;
    for (JsonElement element : a) {
      c[i++] = element.getAsInt();
    }

    result.captchaType = CaptchaType.V3Slide;

    // 取验证码图片
    var server = "https://" + getRes.get("static_servers").getAsJsonArray().get(0).getAsString();
    var origin = GeetestLib.executeToByteArray(newRequestBuilder(server + getRes.get("fullbg").getAsString()).build(), okhttp);
    var notch = GeetestLib.executeToByteArray(newRequestBuilder(server + getRes.get("bg").getAsString()).build(), okhttp);

    result.captchaImages.add(server + getRes.get("fullbg").getAsString());
    result.captchaImages.add(server + getRes.get("bg").getAsString());

    long startTime = System.currentTimeMillis();
    int x = SlideNotchFinder.findNotchX(origin, notch, true, false);
    long inferEnd = System.currentTimeMillis();
    result.inferenceMillis = inferEnd - startTime;
    String w = JsEngine.V3Slide.w(request.gt, challenge, c, s, x);
    result.wGenerationMillis = System.currentTimeMillis() - inferEnd;
    result.validate = validate(challenge, w);

    return result;
  }

  private String validate(String challenge, String w) {
    var ajax = newRequestBuilder(
      "https://api.geetest.com/ajax.php?gt=%s&challenge=%s&lang=zh-cn&%%24_BCN=0&pt=0&client_type=web&w=%s&callback=%s",
      request.gt,
      challenge,
      w,
      newGeetestCallback()
    ).build();

    JsonObject ajaxRes = GeetestLib.execute(ajax, okhttp);

    if (ajaxRes == null) return null;
    if (ajaxRes.has("validate")) return ajaxRes.get("validate").getAsString();
    if (ajaxRes.has("data")) {
      JsonObject data = ajaxRes.get("data").getAsJsonObject();
      return data.has("validate") ? data.get("validate").getAsString() : null;
    }
    return null;
  }


  private String newGeetestCallback() {
    return "geetest_" + System.currentTimeMillis();
  }

  private Request.Builder newRequestBuilder(String urlFormat, Object... urlArgs) {
    String url;
    if (urlArgs != null && urlArgs.length > 0) {
      url = String.format(urlFormat, urlArgs);
    } else {
      url = urlFormat;
    }
    Request.Builder builder = new Request.Builder()
      .url(url)
      .header("Accept-Language", "zh-CN,zh;q=0.9")
      .header("Cache-Control", "no-cache")
      .header("Referer", "https://www.geetest.com")
      .header("Sec-Ch-Ua-Mobile", "?0")
      .header("Sec-Ch-Ua-Platform", "\"Windows\"");
    if (request.referer != null) {
      builder.header("Referer", request.referer);
    }
    if (request.userAgent != null) {
      builder.header("User-Agent", request.userAgent);
    }
    return builder;
  }
}