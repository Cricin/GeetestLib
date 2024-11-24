package com.demo.geetest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** 调用本机器上运行的ai打码服务获取点选位置信息 */
public class ClickInferenceService {
  public static final float Image_Size = 344F;// 极验验证码大小(像素)
  public static final int Size_Factor = 10000;//极验验证坐标缩放值

  /* 本地验证码识别服务加签验证需要的参数 */
  public static final String Hash_Algorithm = "HmacSHA256";
  public static final String Hash_Salt = "you_are_a_bad_man";

  private static final Logger logger = LoggerFactory.getLogger(ClickInferenceService.class);
  private static final MediaType JsonType = MediaType.get("application/json");

  /* 检查本地打码状态相关字段 */
  private static long localCheckTime;
  private static boolean localOnline;

  /** 判断本地打码服务是否在线, 30秒检查一次 */
  public static boolean isAvailable() {
    synchronized (ClickInferenceService.class) {
      long millis = System.currentTimeMillis();
      if (localCheckTime + 10_000 < millis) {
        SocketAddress sa = new InetSocketAddress("127.0.0.1", 12006);
        try (Socket socket = new Socket()) {
          socket.connect(sa, 1000);
          localOnline = true;
          logger.info("本地打码服务已在线！");
        } catch (IOException e) {
          localOnline = false;
          logger.warn("本地打码服务不在线！");
        } finally {
          localCheckTime = System.currentTimeMillis();
        }
      }
    }
    return localOnline;
  }

  public static String inference(Logger logger, byte[] imageBytes, CaptchaType type) {
    logger.info("本地打码，类型={}", type);
    String url;
    switch (type) {
      case V3ClickWord:
        url = "http://localhost:12006/service/geetest-word";
        break;
      case V3ClickIcon:
        url = "http://localhost:12006/service/geetest-icon";
        break;
      case V3ClickPhrase:
        url = "http://localhost:12006/service/geetest-phrase";
        break;
      default:
        throw new RuntimeException("不支持的识别类型: " + type);
    }
    JsonObject body = new JsonObject();
    body.addProperty("base64Image", ByteString.of(imageBytes).base64());
    String bodyStr = sign(body).toString();
    Request request = new Request.Builder().post(RequestBody.create(JsonType, bodyStr)).url(url).build();
    JsonObject res = GeetestLib.execute(request, GeetestLib.okhttp);


    logger.debug("本地打码识别res={}", res);
    if (res == null || res.get("code").getAsInt() != 0) return null;
    JsonArray array = res.get("data").getAsJsonArray();

    List<String> points = new ArrayList<>();
    for (JsonElement d : array) {
      JsonArray pos = d.getAsJsonArray();
      int x = pos.get(0).getAsInt();
      int y = pos.get(1).getAsInt();
      int x1 = pos.get(2).getAsInt();
      int y1 = pos.get(3).getAsInt();
      int centerX = (x + x1) / 2;
      int centerY = (y + y1) / 2;
      String point = ((int) (centerX / Image_Size * Size_Factor)) + "_" + ((int) (centerY / Image_Size * Size_Factor));
      points.add(point);
    }
    return String.join(",", points);
  }

  private static JsonObject sign(JsonObject obj) {
    if (!obj.has("timestamp")) {
      obj.addProperty("timestamp", System.currentTimeMillis());
    }
    String json = obj.toString();
    byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);
    byte[] keyBytes = Hash_Salt.getBytes(StandardCharsets.UTF_8);
    try {
      Mac mac = Mac.getInstance(Hash_Algorithm);
      mac.init(new SecretKeySpec(keyBytes, Hash_Algorithm));
      String sign = ByteString.of(mac.doFinal(dataBytes)).hex();
      obj.addProperty("sign", sign);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    return obj;
  }
}