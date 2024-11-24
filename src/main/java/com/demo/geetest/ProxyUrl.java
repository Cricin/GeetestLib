package com.demo.geetest;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class ProxyUrl {
  private static final Pattern IPV4_PATTERN = Pattern.compile(
    "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

  private final String url;
  private final String scheme;
  private final String host;
  private final int port;
  private final String username;
  private final String password;
  private String ip;

  public ProxyUrl(String url, String scheme, String host, int port, String username, String password) {
    this.scheme = scheme;
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
    this.url = url;
  }

  public String scheme() {
    return scheme;
  }

  public int port() {
    return port;
  }

  public String ip() {
    if (ip != null) return ip;
    if (IPV4_PATTERN.matcher(host).matches()) return host;
    // resolve host to ip address
    try {
      InetAddress address = InetAddress.getByName(host);
      ip = address.getHostAddress();
    } catch (UnknownHostException e) {
      ip = "0.0.0.0";
    }
    return ip;
  }

  public OkHttpClient apply(OkHttpClient base) {
    OkHttpClient.Builder builder = base.newBuilder();
    switch (scheme) {
      case "socks":
      case "socks5":
        builder.proxy(new Proxy(Proxy.Type.SOCKS, hostSocketAddress())).build();
        break;
      case "http":
        builder.proxy(new Proxy(Proxy.Type.HTTP, hostSocketAddress()));
        if (username != null && !username.isBlank()) { //有登录信息,需要验证
          builder.proxyAuthenticator(new Authenticator() {
            @Override
            public Request authenticate(Route route, Response response) {
              if (response.request().header("Proxy-Authorization") != null)
                return null; // Give up, we've already failed to authenticate.
              String credential = Credentials.basic(username, password);
              return response.request().newBuilder().header("Proxy-Authorization", credential).build();
            }
          });
        }
        break;
      default:
        throw new RuntimeException("Okhttp不支持的代理模式: " + scheme);
    }
    return builder.build();
  }

  private SocketAddress hostSocketAddress() {
    return new InetSocketAddress(host, port);
  }

  @Override
  public String toString() {
    return url;
  }

  /**
   *     1. socks://host:port
   *     2. socks5://host:port
   *     3. http://host:port
   *     4. http://username:password@host:port
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  public static ProxyUrl parse(String input) {
    String scheme;
    String host;
    String username;
    String password;
    int port;

    try {
      String[] segments = input.split("://");
      scheme = segments[0].toLowerCase();
      int at = segments[1].indexOf("@");
      String hostAndPort;
      if (at >= 0) { // 有用户名和密码
        String[] split = segments[1].split("@");
        username = split[0].split(":")[0];
        password = split[0].split(":")[1];
        hostAndPort = split[1];
      } else {
        username = null;
        password = null;
        hostAndPort = segments[1];
      }
      host = hostAndPort.split(":")[0];
      port = Integer.parseInt(hostAndPort.split(":")[1]);
      return new ProxyUrl(input, scheme, host, port, username, password);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to parse proxy url: " + input, e);
    }
  }

  public static boolean isDirectOrSystem(String input) {
    if (input == null) return true;
    if (input.isBlank()) return true;
    String inputLowerCase = input.toLowerCase();
    return inputLowerCase.contains("direct") || inputLowerCase.contains("system");
  }
}