package com.github.sd4324530.fastweixin.servlet;

import com.github.sd4324530.fastweixin.handle.EventHandle;
import com.github.sd4324530.fastweixin.handle.MessageHandle;
import com.github.sd4324530.fastweixin.message.BaseMsg;
import com.github.sd4324530.fastweixin.message.TextMsg;
import com.github.sd4324530.fastweixin.message.aes.AesException;
import com.github.sd4324530.fastweixin.message.aes.WXBizMsgCrypt;
import com.github.sd4324530.fastweixin.message.req.*;
import com.github.sd4324530.fastweixin.util.MessageUtil;
import com.github.sd4324530.fastweixin.util.SignUtil;
import com.github.sd4324530.fastweixin.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import static com.github.sd4324530.fastweixin.util.BeanUtil.isNull;
import static com.github.sd4324530.fastweixin.util.BeanUtil.nonNull;
import static com.github.sd4324530.fastweixin.util.CollectionUtil.isEmpty;
import static com.github.sd4324530.fastweixin.util.CollectionUtil.isNotEmpty;
import static com.github.sd4324530.fastweixin.util.StrUtil.isNotBlank;

/**
 * 将微信处理通用部分再抽象一层，使用其他框架框架的同学可以自行继承此类集成微信
 *
 * @author peiyu
 * @since 1.1
 */
public abstract class WeixinSupport {

    private static final Logger LOG  = LoggerFactory.getLogger(WeixinSupport.class);
    //充当锁
    private static final Object LOCK = new Object();

//    protected String fromUserName, toUserName;

    /**
     * 微信消息处理器列表
     */
    private static List<MessageHandle> messageHandles;
    /**
     * 微信事件处理器列表
     */
    private static List<EventHandle>   eventHandles;

    /**
     * 子类重写，加入自定义的微信消息处理器，细化消息的处理
     *
     * @return 微信消息处理器列表
     */
    protected List<MessageHandle> initMessageHandles() {
        return null;
    }

    /**
     * 子类重写，加入自定义的微信事件处理器，细化消息的处理
     *
     * @return 微信事件处理器列表
     */
    protected List<EventHandle> initEventHandles() {
        return null;
    }

    /**
     * 子类提供token用于绑定微信公众平台
     *
     * @return token值
     */
    protected abstract String getToken(HttpServletRequest request);

    /**
     * 公众号APPID，使用消息加密模式时用户自行设置
     *
     * @return 微信公众平台提供的appid
     */
    protected String getAppId(HttpServletRequest request) {
        return null;
    }

    /**
     * 加密的密钥，使用消息加密模式时用户自行设置
     *
     * @return 用户自定义的密钥
     */
    protected String getAESKey(HttpServletRequest request) {
        return null;
    }

    /**
     * 绑定服务器的方法
     * @param request 请求
     * @param response 响应
     */
    public void bindServer(HttpServletRequest request, HttpServletResponse response) {
        if (isLegal(request)) {
            try {
                PrintWriter pw = response.getWriter();
                pw.write(request.getParameter("echostr"));
                pw.flush();
                pw.close();
            } catch (Exception e) {
                LOG.error("绑定服务器异常", e);
            }
        }
    }

    /**
     * 处理微信服务器发来的请求方法
     *
     * @param request http请求对象
     * @return 处理消息的结果，已经是接口要求的xml报文了
     */
    public String processRequest(HttpServletRequest request) {
        Map<String, Object> reqMap = MessageUtil.parseXml(request, getToken(request), getAppId(request), getAESKey(request));
        String fromUserName = (String) reqMap.get("FromUserName");
        String toUserName = (String) reqMap.get("ToUserName");
        String msgType = (String) reqMap.get("MsgType");

        LOG.debug("收到消息,消息类型:{}", msgType);

        BaseMsg msg = null;

        if (msgType.equals(ReqType.EVENT)) {
            String eventType = (String) reqMap.get("Event");
            String ticket = (String) reqMap.get("Ticket");
            QrCodeEvent qrCodeEvent = null;
            if (isNotBlank(ticket)) {
                String eventKey = (String) reqMap.get("EventKey");
                LOG.debug("eventKey:{}", eventKey);
                LOG.debug("ticket:{}", ticket);
                qrCodeEvent = new QrCodeEvent(eventKey, ticket);
                buildBasicEvent(reqMap, qrCodeEvent);
                if (eventType.equals(EventType.SCAN)) {
                    msg = handleQrCodeEvent(request,qrCodeEvent);
                    if (isNull(msg)) {
                        msg = processEventHandle(request,qrCodeEvent);
                    }
                }
            }
            if (eventType.equals(EventType.SUBSCRIBE)) {
                BaseEvent event = new BaseEvent();
                if (qrCodeEvent != null) {
                    event = qrCodeEvent;
                } else {
                    buildBasicEvent(reqMap, event);
                }
                msg = handleSubscribe(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (eventType.equals(EventType.UNSUBSCRIBE)) {
                BaseEvent event = new BaseEvent();
                buildBasicEvent(reqMap, event);
                msg = handleUnsubscribe(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (eventType.equals(EventType.CLICK)) {
                String eventKey = (String) reqMap.get("EventKey");
                LOG.debug("eventKey:{}", eventKey);
                MenuEvent event = new MenuEvent(eventKey);
                buildBasicEvent(reqMap, event);
                msg = handleMenuClickEvent(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (eventType.equals(EventType.VIEW)) {
                String eventKey = (String) reqMap.get("EventKey");
                LOG.debug("eventKey:{}", eventKey);
                MenuEvent event = new MenuEvent(eventKey);
                buildBasicEvent(reqMap, event);
                msg = handleMenuViewEvent(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (eventType.equals(EventType.LOCATION)) {
                double latitude = Double.parseDouble((String) reqMap.get("Latitude"));
                double longitude = Double.parseDouble((String) reqMap.get("Longitude"));
                double precision = Double.parseDouble((String) reqMap.get("Precision"));
                LocationEvent event = new LocationEvent(latitude, longitude,
                        precision);
                buildBasicEvent(reqMap, event);
                msg = handleLocationEvent(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (EventType.SCANCODEPUSH.equals(eventType) || EventType.SCANCODEWAITMSG.equals(eventType)) {
                String eventKey = (String) reqMap.get("EventKey");
                Map<String, Object> scanCodeInfo = (Map<String, Object>)reqMap.get("ScanCodeInfo");
                String scanType = (String) scanCodeInfo.get("ScanType");
                String scanResult = (String) scanCodeInfo.get("ScanResult");
                ScanCodeEvent event = new ScanCodeEvent(eventKey, scanType, scanResult);
                buildBasicEvent(reqMap, event);
                msg = handleScanCodeEvent(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (EventType.PICPHOTOORALBUM.equals(eventType) || EventType.PICSYSPHOTO.equals(eventType) || EventType.PICWEIXIN.equals(eventType)) {
                String eventKey = (String) reqMap.get("EventKey");
                Map<String, Object> sendPicsInfo = (Map<String, Object>)reqMap.get("SendPicsInfo");
                int count = Integer.parseInt((String) sendPicsInfo.get("Count"));
                List<Map> picList = (List) sendPicsInfo.get("PicList");
                SendPicsInfoEvent event = new SendPicsInfoEvent(eventKey, count, picList);
                buildBasicEvent(reqMap, event);
                msg = handlePSendPicsInfoEvent(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            } else if (EventType.TEMPLATESENDJOBFINISH.equals(eventType)) {
                String msgId = (String) reqMap.get("MsgID");
                String status = (String) reqMap.get("Status");
                TemplateMsgEvent event = new TemplateMsgEvent(msgId,status);
                buildBasicEvent(reqMap, event);
                msg = handleTemplateMsgEvent(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            }else if(EventType.MASSSENDJOBFINISH.equals(eventType)){
                String msgId=(String)reqMap.get("MsgID");
                String status=(String)reqMap.get("Status");
                Integer TotalCount=Integer.valueOf(String.valueOf(reqMap.get("TotalCount")));
                Integer filterCount=Integer.valueOf(String.valueOf(reqMap.get("FilterCount")));
                Integer sentCount=Integer.valueOf(String.valueOf(reqMap.get("SentCount")));
                Integer errorCount=Integer.valueOf(String.valueOf(reqMap.get("ErrorCount")));
                SendMessageEvent event=new SendMessageEvent(msgId,status,TotalCount,filterCount,sentCount,errorCount);
                buildBasicEvent(reqMap, event);
                msg=callBackAllMessage(request,event);
                if (isNull(msg)) {
                    msg = processEventHandle(request,event);
                }
            }
        } else {
            if (msgType.equals(ReqType.TEXT)) {
                String content = (String) reqMap.get("Content");
                LOG.debug("文本消息内容:{}", content);
                TextReqMsg textReqMsg = new TextReqMsg(content);
                buildBasicReqMsg(reqMap, textReqMsg);
                msg = handleTextMsg(request,textReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,textReqMsg);
                }
            } else if (msgType.equals(ReqType.IMAGE)) {
                String picUrl = (String) reqMap.get("PicUrl");
                String mediaId = (String) reqMap.get("MediaId");
                ImageReqMsg imageReqMsg = new ImageReqMsg(picUrl, mediaId);
                buildBasicReqMsg(reqMap, imageReqMsg);
                msg = handleImageMsg(request,imageReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,imageReqMsg);
                }
            } else if (msgType.equals(ReqType.VOICE)) {
                String format = (String) reqMap.get("Format");
                String mediaId = (String) reqMap.get("MediaId");
                String recognition = (String) reqMap.get("Recognition");
                VoiceReqMsg voiceReqMsg = new VoiceReqMsg(mediaId, format,
                        recognition);
                buildBasicReqMsg(reqMap, voiceReqMsg);
                msg = handleVoiceMsg(request,voiceReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,voiceReqMsg);
                }
            } else if (msgType.equals(ReqType.VIDEO)) {
                String thumbMediaId = (String) reqMap.get("ThumbMediaId");
                String mediaId = (String) reqMap.get("MediaId");
                VideoReqMsg videoReqMsg = new VideoReqMsg(mediaId, thumbMediaId);
                buildBasicReqMsg(reqMap, videoReqMsg);
                msg = handleVideoMsg(request,videoReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,videoReqMsg);
                }
            } else if (msgType.equals(ReqType.SHORT_VIDEO)) {
                String thumbMediaId = (String) reqMap.get("ThumbMediaId");
                String mediaId = (String) reqMap.get("MediaId");
                VideoReqMsg videoReqMsg = new VideoReqMsg(mediaId, thumbMediaId);
                buildBasicReqMsg(reqMap, videoReqMsg);
                msg = hadnleShortVideoMsg(request,videoReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,videoReqMsg);
                }
            } else if (msgType.equals(ReqType.LOCATION)) {
                double locationX = Double.parseDouble((String) reqMap.get("Location_X"));
                double locationY = Double.parseDouble((String) reqMap.get("Location_Y"));
                int scale = Integer.parseInt((String) reqMap.get("Scale"));
                String label = (String) reqMap.get("Label");
                LocationReqMsg locationReqMsg = new LocationReqMsg(locationX,
                        locationY, scale, label);
                buildBasicReqMsg(reqMap, locationReqMsg);
                msg = handleLocationMsg(request,locationReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,locationReqMsg);
                }
            } else if (msgType.equals(ReqType.LINK)) {
                String title = (String) reqMap.get("Title");
                String description = (String) reqMap.get("Description");
                String url = (String) reqMap.get("Url");
                LOG.debug("链接消息地址:{}", url);
                LinkReqMsg linkReqMsg = new LinkReqMsg(title, description, url);
                buildBasicReqMsg(reqMap, linkReqMsg);
                msg = handleLinkMsg(request,linkReqMsg);
                if (isNull(msg)) {
                    msg = processMessageHandle(request,linkReqMsg);
                }
            }
        }
        String result = "";
        if (nonNull(msg)) {
            msg.setFromUserName(toUserName);
            msg.setToUserName(fromUserName);
            result = msg.toXml();
            if (StrUtil.isNotBlank(getAESKey(request))) {
                try {
                    WXBizMsgCrypt pc = new WXBizMsgCrypt(getToken(request), getAESKey(request), getAppId(request));
                    result = pc.encryptMsg(result, request.getParameter("timestamp"), request.getParameter("nonce"));
                    LOG.debug("加密后密文:{}", result);
                } catch (AesException e) {
                    LOG.error("加密异常", e);
                }
            }
        }
        return result;
    }

    private BaseMsg processMessageHandle(HttpServletRequest request,BaseReqMsg msg) {
        if (isEmpty(messageHandles)) {
            synchronized (LOCK) {
                messageHandles = this.initMessageHandles();
            }
        }
        if (isNotEmpty(messageHandles)) {
            for (MessageHandle messageHandle : messageHandles) {
                BaseMsg resultMsg = null;
                boolean result;
                try {
                    result = messageHandle.beforeHandle(request,msg);
                } catch (Exception e) {
                    result = false;
                }
                if (result) {
                    resultMsg = messageHandle.handle(request,msg);
                }
                if (nonNull(resultMsg)) {
                    return resultMsg;
                }
            }
        }
        return null;
    }

    private BaseMsg processEventHandle(HttpServletRequest request,BaseEvent event) {
        if (isEmpty(eventHandles)) {
            synchronized (LOCK) {
                eventHandles = this.initEventHandles();
            }
        }
        if (isNotEmpty(eventHandles)) {
            for (EventHandle eventHandle : eventHandles) {
                BaseMsg resultMsg = null;
                boolean result;
                try {
                    result = eventHandle.beforeHandle(request,event);
                } catch (Exception e) {
                    result = false;
                }
                if (result) {
                    resultMsg = eventHandle.handle(request,event);
                }
                if (nonNull(resultMsg)) {
                    return resultMsg;
                }
            }
        }
        return null;
    }

    /**
     * 处理文本消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg handleTextMsg(HttpServletRequest request,TextReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理图片消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg handleImageMsg(HttpServletRequest request,ImageReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理语音消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg handleVoiceMsg(HttpServletRequest request,VoiceReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理视频消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg handleVideoMsg(HttpServletRequest request,VideoReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理小视频消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg hadnleShortVideoMsg(HttpServletRequest request,VideoReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理地理位置消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg handleLocationMsg(HttpServletRequest request,LocationReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理链接消息，有需要时子类重写
     *
     * @param msg 请求消息对象
     * @return 响应消息对象
     */
    protected BaseMsg handleLinkMsg(HttpServletRequest request,LinkReqMsg msg) {
        return handleDefaultMsg(request,msg);
    }

    /**
     * 处理扫描二维码事件，有需要时子类重写
     *
     * @param event 扫描二维码事件对象
     * @return 响应消息对象
     */
    protected BaseMsg handleQrCodeEvent(HttpServletRequest request,QrCodeEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理地理位置事件，有需要时子类重写
     *
     * @param event 地理位置事件对象
     * @return 响应消息对象
     */
    protected BaseMsg handleLocationEvent(HttpServletRequest request,LocationEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理菜单点击事件，有需要时子类重写
     *
     * @param event 菜单点击事件对象
     * @return 响应消息对象
     */
    protected BaseMsg handleMenuClickEvent(HttpServletRequest request,MenuEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理菜单跳转事件，有需要时子类重写
     *
     * @param event 菜单跳转事件对象
     * @return 响应消息对象
     */
    protected BaseMsg handleMenuViewEvent(HttpServletRequest request,MenuEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理菜单扫描推事件，有需要时子类重写
     *
     * @param event 菜单扫描推事件对象
     * @return 响应的消息对象
     */
    protected BaseMsg handleScanCodeEvent(HttpServletRequest request,ScanCodeEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理菜单弹出相册事件，有需要时子类重写
     *
     * @param event 菜单弹出相册事件
     * @return 响应的消息对象
     */
    protected BaseMsg handlePSendPicsInfoEvent(HttpServletRequest request,SendPicsInfoEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理模版消息发送事件，有需要时子类重写
     *
     * @param event 菜单弹出相册事件
     * @return 响应的消息对象
     */
    protected BaseMsg handleTemplateMsgEvent(HttpServletRequest request,TemplateMsgEvent event) {
        return handleDefaultEvent(request,event);
    }

    /**
     * 处理添加关注事件，有需要时子类重写
     *
     * @param event 添加关注事件对象
     * @return 响应消息对象
     */
    protected BaseMsg handleSubscribe(HttpServletRequest request,BaseEvent event) {
        return new TextMsg("感谢您的关注!");
    }

    /**
     * 接收群发消息的回调方法
     *
     * @param event 群发回调方法
     * @return 响应消息对象
     */
    protected  BaseMsg callBackAllMessage(HttpServletRequest request,SendMessageEvent event){return  handleDefaultEvent(request,event);}

    /**
     * 处理取消关注事件，有需要时子类重写
     *
     * @param event 取消关注事件对象
     * @return 响应消息对象
     */
    protected BaseMsg handleUnsubscribe(HttpServletRequest request,BaseEvent event) {
        return null;
    }

    protected BaseMsg handleDefaultMsg(HttpServletRequest request,BaseReqMsg msg) {
        return null;
    }

    protected BaseMsg handleDefaultEvent(HttpServletRequest request,BaseEvent event) {
        return null;
    }

    private void buildBasicReqMsg(Map<String, Object> reqMap, BaseReqMsg reqMsg) {
        addBasicReqParams(reqMap, reqMsg);
        reqMsg.setMsgId((String) reqMap.get("MsgId"));
    }

    private void buildBasicEvent(Map<String, Object> reqMap, BaseEvent event) {
        addBasicReqParams(reqMap, event);
        event.setEvent((String) reqMap.get("Event"));
    }

    private void addBasicReqParams(Map<String, Object> reqMap, BaseReq req) {
        req.setMsgType((String) reqMap.get("MsgType"));
        req.setFromUserName((String) reqMap.get("FromUserName"));
        req.setToUserName((String) reqMap.get("ToUserName"));
        req.setCreateTime(Long.parseLong((String) reqMap.get("CreateTime")));
    }

    protected boolean isLegal(HttpServletRequest request) {
        String signature = request.getParameter("signature");
        String timestamp = request.getParameter("timestamp");
        String nonce = request.getParameter("nonce");
        return SignUtil.checkSignature(getToken(request), signature, timestamp, nonce);
    }
}