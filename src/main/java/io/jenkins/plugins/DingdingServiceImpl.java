package io.jenkins.plugins;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Created by lishaowei on 16/10/8.
 */
public class DingdingServiceImpl implements DingdingService {

    private Logger logger = LoggerFactory.getLogger(DingdingService.class);

    private String jenkinsURL;

    private boolean onStart;

    private boolean onSuccess;

    private boolean onFailed;

    private boolean onAbort;

    private boolean fireline;

    private TaskListener listener;

    private AbstractBuild build;

    private static final String apiUrl = "https://oapi.dingtalk.com/robot/send?access_token=";

    private ArrayList<String> apiList = new ArrayList<>();
    private EnvVars env;
    private String[] mobilePhone;

    public DingdingServiceImpl(String jenkinsURL, String token, boolean onStart, boolean onSuccess, boolean onFailed, boolean onAbort, TaskListener listener, AbstractBuild build, String mobilePhone, boolean fireline) {
        this.jenkinsURL = jenkinsURL;
        this.onStart = onStart;
        this.onSuccess = onSuccess;
        this.onFailed = onFailed;
        this.onAbort = onAbort;
        this.listener = listener;
        this.build = build;
//        this.api = apiUrl + dingdingToken(token);
        this.apiList = getApiList(apiUrl, dingdingToken(token));
        this.mobilePhone = getMobilePhoneArray(mobilePhone);
        this.fireline = fireline;
    }

    private String[] getMobilePhoneArray(String phone) {
        try {
            env = build.getEnvironment(listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String mobilephone = env.expand(phone);
        return mobilephone.split(",");
    }

    private ArrayList<String> getApiList(String apiUrl, String s) {
        ArrayList<String> arrayList = new ArrayList<>();
        String[] split = s.split(",");
        for (int i = 0; i < split.length; i++) {
            arrayList.add(apiUrl + split[i]);
        }
        return arrayList;
    }

    public String dingdingToken(String token) {
        try {
            env = build.getEnvironment(listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return env.expand(token);
    }

    @Override
    public void start() {
        String pic = "http://icon-park.com/imagefiles/loading7_gray.gif";
        String title = String.format("%s%s开始构建", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]开始构建", build.getProject().getDisplayName(), build.getDisplayName());

        String link = getBuildUrl();
        if (onStart) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }
    }

    private String getBuildUrl() {
        if (jenkinsURL.endsWith("/")) {
            return jenkinsURL + build.getUrl();
        } else {
            return jenkinsURL + "/" + build.getUrl();
        }
    }

    @Override
    public void success() {
        String pic = "http://icons.iconarchive.com/icons/paomedia/small-n-flat/1024/sign-check-icon.png";
        String title = String.format("%s%s构建成功", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]构建成功, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onSuccess) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }
        if (fireline) {
            sendFireLineMessage(build.getProject().getDisplayName());
        }
    }

    @Override
    public void failed() {
        String pic = "http://www.iconsdb.com/icons/preview/soylent-red/x-mark-3-xxl.png";
        String title = String.format("%s%s构建失败", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]构建失败, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onFailed) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
            sendNotifyPersonMessage(link, content, title, pic);
        }
    }


    @Override
    public void abort() {
        String pic = "http://www.iconsdb.com/icons/preview/soylent-red/x-mark-3-xxl.png";
        String title = String.format("%s%s构建中断", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]构建中断, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onAbort) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
            sendNotifyPersonMessage(link, content, title, pic);
        }
    }

    private void sendNotifyPersonMessage(String link, String content, String title, String pic) {
        HttpClient client = getHttpClient();

        JSONObject body = new JSONObject();
        body.put("msgtype", "text");


        JSONObject linkObject = new JSONObject();
//        我就是我,  @1825718XXXX 是不一样的烟火
        linkObject.put("content", content);
        body.put("text", linkObject);

        JSONObject atJson = new JSONObject();
        atJson.put("atMobiles", mobilePhone);
        atJson.put("isAtAll", false);
        body.put("at", atJson);
        for (int i = 0; i < apiList.size(); i++) {
            PostMethod post = new PostMethod(apiList.get(i));
            try {
                post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                logger.error("build request error", e);
            }
            try {
                client.executeMethod(post);
                logger.info(post.getResponseBodyAsString());
            } catch (IOException e) {
                logger.error("send msg error", e);
            }
            post.releaseConnection();
        }
    }

    private void sendLinkMessage(String link, String msg, String title, String pic) {
        HttpClient client = getHttpClient();

        JSONObject body = new JSONObject();
        body.put("msgtype", "link");


        JSONObject linkObject = new JSONObject();
        linkObject.put("text", msg);
        linkObject.put("title", title);
        linkObject.put("picUrl", pic);
        linkObject.put("messageUrl", link);

        body.put("link", linkObject);
        for (int i = 0; i < apiList.size(); i++) {
            PostMethod post = new PostMethod(apiList.get(i));
            try {
                post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                logger.error("build request error", e);
            }
            try {
                client.executeMethod(post);
                logger.info(post.getResponseBodyAsString());
            } catch (IOException e) {
                logger.error("send msg error", e);
            }
            post.releaseConnection();
        }
    }
    /**
     * 发送静态检测代码 link 形式
     */
    private void sendFireLineMessage(String projectName) {
        HttpClient client = getHttpClient();

        JSONObject body = new JSONObject();
        body.put("msgtype", "link");

        JSONObject linkObject = new JSONObject();
        linkObject.put("text", projectName + "静态代码检测报告已经出炉，请查看");
        linkObject.put("title", projectName + "静态代码检测报告");
        linkObject.put("picUrl", "https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1532007477320&di=b2a8321bda5171566836f0f29abec90a&imgtype=0&src=http%3A%2F%2Fimg.zcool.cn%2Fcommunity%2F018fc857b1871f0000012e7ec3eb73.jpg");
        linkObject.put("messageUrl", "http://10.4.2.65:8080/job/android-fireline-xesapp/HTML_20Report/");

        body.put("link", linkObject);
        for (int i = 0; i < apiList.size(); i++) {
            PostMethod post = new PostMethod(apiList.get(i));
            try {
                post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                logger.error("build request error", e);
            }
            try {
                client.executeMethod(post);
                logger.info(post.getResponseBodyAsString());
            } catch (IOException e) {
                logger.error("send msg error", e);
            }
            post.releaseConnection();
        }
    }

    /**
     * 发送静态检测代码 action 形式
     */
    public void sendActionMessage(String projectName) {

        HttpClient client = getHttpClient();
        JSONObject body = new JSONObject();
        body.put("msgtype", "actionCard");

        JSONObject linkObject = new JSONObject();
        linkObject.put("text", "![screenshot](https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1532001735553&di=973166a5ba1629879a419095b01461e0&imgtype=0&src=http%3A%2F%2Fimgsrc.baidu.com%2Fimgad%2Fpic%2Fitem%2Ffaf2b2119313b07e46a0aa3307d7912397dd8ce8.jpg) \n #### 静态代码检测报告 \n\n " + projectName + "静态代码检测报告新鲜出炉，请查收！");
        linkObject.put("title", projectName + "静态代码检测报告");
        linkObject.put("hideAvatar", "0");
        linkObject.put("btnOrientation", "0");
        linkObject.put("singleTitle", "查看全部");
        linkObject.put("singleURL", "http://10.4.2.65:8080/job/android-fireline-xesapp/HTML_20Report/");

        body.put("actionCard", linkObject);
        for (int i = 0; i < apiList.size(); i++) {
            PostMethod post = new PostMethod(apiList.get(i));
            try {
                post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                logger.error("build request error", e);
            }
            try {
                client.executeMethod(post);
                logger.info(post.getResponseBodyAsString());
            } catch (IOException e) {
                logger.error("send msg error", e);
            }
            post.releaseConnection();
        }
    }

    /**
     * 发送静态检测代码 feedinfo 形式
     */
    private void sendFeedInfoMessage(String projectName) {
        HttpClient client = getHttpClient();

        JSONObject body = new JSONObject();

        JSONObject feedCardObject = new JSONObject();
        JSONArray linksArray = new JSONArray();
        JSONObject linkObject = new JSONObject();
        linkObject.put("title", projectName + "静态代码检测报告");
        linkObject.put("picURL", "https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1532002770381&di=0b273bd59b97de7875233ff6aa0ed560&imgtype=0&src=http%3A%2F%2Fimg2.mukewang.com%2F57d66de200014a7306000338.jpg");
        linkObject.put("messageURL", "http://10.4.2.65:8080/job/android-fireline-xesapp/HTML_20Report/");
        linksArray.add(linkObject);

        feedCardObject.put("links", linksArray);
        body.put("feedCard", feedCardObject);
        body.put("msgtype", "feedCard");
        for (int i = 0; i < apiList.size(); i++) {
            PostMethod post = new PostMethod(apiList.get(i));
            try {
                post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                logger.error("build request error", e);
            }
            try {
                client.executeMethod(post);
                logger.info(post.getResponseBodyAsString());
            } catch (IOException e) {
                logger.error("send msg error", e);
            }
            post.releaseConnection();
        }
    }

    private static HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.proxy != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null && client.getHostConfiguration() != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
//                    logger.info("Using proxy authentication (user=" + username + ")");
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }
}
