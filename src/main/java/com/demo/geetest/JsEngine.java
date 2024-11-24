package com.demo.geetest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.nio.charset.StandardCharsets;

/**
 * 调用破解后的极验js代码生成w值
 * java执行js代码依赖于nashorn引擎，在JDK11上是自带的，但是高版本去掉了，需要单独添加maven依赖
 * */
public final class JsEngine {
  static {
    // 配置js引擎不显示warning信息
    System.setProperty("nashorn.args", "--no-deprecation-warning");
  }

  private static final Logger logger = LoggerFactory.getLogger(JsEngine.class);
  public static final JsEngine V3Click = new JsEngine("click.3.0.9_break.js", "myGenerateW");
  public static final JsEngine V3Slide = new JsEngine("slide.7.9.2_break.js", "myGenerateW");

  private final String function;
  private final Invocable engine;

  private JsEngine(String resName, String function) {
    this.function = function;
    String jsCode = readClasspathUtf8(resName);
    try {
      ScriptEngineManager manager = new ScriptEngineManager();
      ScriptEngine js = manager.getEngineByName("JavaScript");
      js.eval(jsCode);
      engine = (Invocable) js;
    } catch (Throwable t) {
      throw new RuntimeException("Error initializing JavaScript engine", t);
    }
  }

  /**
   * click传参顺序为 gt, challenge, pic, passTime, points
   * slide传参顺序为 gt, challenge, c, s, x
   * */
  public String w(Object... args) {
    try {
      return (String) engine.invokeFunction(function, args);
    } catch (Exception e) {
      logger.error("Error generating w", e);
    }
    return null;
  }

  private static String readClasspathUtf8(String resource) {
    var loader = Thread.currentThread().getContextClassLoader();
    try (var in = loader.getResourceAsStream(resource)) {
      if (in == null) throw new RuntimeException("Resource not found: " + resource);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("Error reading resource: " + resource, e);
    }
  }
}